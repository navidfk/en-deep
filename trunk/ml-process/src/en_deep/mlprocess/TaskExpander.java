/*
 *  Copyright (c) 2010 Ondrej Dusek
 *  All rights reserved.
 * 
 *  Redistribution and use in source and binary forms, with or without modification, 
 *  are permitted provided that the following conditions are met:
 *  Redistributions of source code must retain the above copyright notice, this list 
 *  of conditions and the following disclaimer.
 *  Redistributions in binary form must reproduce the above copyright notice, this 
 *  list of conditions and the following disclaimer in the documentation and/or other 
 *  materials provided with the distribution.
 *  Neither the name of Ondrej Dusek nor the names of their contributors may be
 *  used to endorse or promote products derived from this software without specific 
 *  prior written permission.
 * 
 *  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND 
 *  ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED 
 *  WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. 
 *  IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, 
 *  INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, 
 *  BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, 
 *  DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF 
 *  LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE 
 *  OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED 
 *  OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package en_deep.mlprocess;

import com.google.common.collect.HashMultimap;
import en_deep.mlprocess.exception.TaskException;
import en_deep.mlprocess.utils.StringUtils;
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Vector;


/**
 * This class expands the wildcard characters in inputs and outputs specifications, possibly creating some
 * more tasks. This affects also the depending tasks in some cases. All the created tasks are put
 * in place of the original tasks. The first task to be processed is returned.
 * <p>
 * All the input "*" characters are expanded and single "*"s in output specifications, too. Only the
 * output "**" characters are not expanded, as the final file names and count are determined
 * by the task itself.
 * </p>
 * <p>
 * Expansion modes (only one pattern per file allowed):
 * </p>
 * <ul>
 * <li>input "*" - all usages must expand correspondingly</li>
 * <li>input "**" - all files just added to inputs of the given task</li>
 * <li>input "***" - carthesian product of tasks is created for each possible combination</li>
 * <li>output "*" - expanded corresponding to the input "*"s, trasitive to depending tasks</li>
 * </ul>
 *
 */
public class TaskExpander {


    /* DATA */

    /** The task to be processed */
    private TaskDescription task;

    /** Positions in the task output listings, where are wildcard patterns to be found */
    private Vector<Integer> inputTrans, inputHere, inputCarth;
    /** Positions in the task input listings, where are wildcard patterns to be found */
    private Vector<Integer> outputTrans, outputHere;

    /** Expansion pattern matches for all affected tasks */
    private HashMultimap<TaskDescription, TaskDescription> expansions;


    /* METHODS */

    /**
     * This creates a new {@link TaskExpander} object, just setting the {@link TaskDescription} to be
     * processed. The expansion process is triggered by {@link TaskExpander#expand()}
     *
     * @param task the task to be expanded
     */
    public TaskExpander(TaskDescription task){

        this.task = task;
        this.expansions = HashMultimap.create();

        this.inputTrans = null;
        this.inputHere = null;
        this.inputCarth = null;

        this.outputTrans = null;
    }


    /**
     * The actual expansion process. When it's finished, the new tasks and tasks to be deleted may be
     * retrieved using {@link TaskExpander#getTasksToRemove()} and {@link TaskExpander#getTasksToAdd()}
     *
     * @throws TaskException if there's a problem with the data specification
     */
    public void expand() throws TaskException {

        this.findPatterns();

        // there are no patterns or just "**"s
        if (this.inputTrans == null && this.inputCarth == null){

            // can't have "*"-patterns in outputs since there are none in inputs
            if (this.outputTrans != null){
               throw new TaskException(TaskException.ERR_PATTERN_SPECS, this.task.getId(),
                       "No '*' patterns in inputs but some in outputs.");
            }
            // expand "**" here if needed
            if (this.inputHere != null){
                this.expandHere();
            }
            return;
        }
        // "**" are not compatible with other types
        else if (this.inputHere != null){
            throw new TaskException(TaskException.ERR_PATTERN_SPECS, this.task.getId(),
                    "'**' patterns cannot be combined with other patterns.");
        }
        // if there are "*"s, expand them all at a time
        else if (this.inputTrans != null){

            Vector<String> taskInput = this.task.getInput();
            Vector<String> matches = this.expandPattern(taskInput.get(this.inputTrans.firstElement()), true);

            for (String match : matches){
                this.expansions.put(this.task, this.task.expand(match));
            }
        }

        // expand "***"s -- one by one
        if (this.inputCarth != null){
            this.expandCarthesian();
        }

        // check if all the outputs have "*" or "**"'s (otherwise, there's no point in using "*" or "***"
        // for inputs)
        if ((this.outputTrans != null && this.outputTrans.size() != task.getOutput().size())
                || (this.outputHere != null && this.outputHere.size() != task.getOutput().size())
                || (this.outputHere == null && this.outputTrans == null)){
            throw new TaskException(TaskException.ERR_PATTERN_SPECS, this.task.getId(),
                    "All outputs must have '*' or '**' patterns if inputs have '*' or '***' patterns.");
        }

        // expand outputs and dependent tasks using the expanded task name (if we can--"**"'s are never expanded)
        if (this.outputHere == null){
            this.expandOutputsAndDeps(this.task);
        }

        // remove dependencies of all original unexpanded tasks (which are selected for removal)
        for (TaskDescription t : this.expansions.keySet()){
            t.looseAllDeps();
        }

    }


    /**
     * Returns the result of the expansion - a list of new expanded tasks that should be added to
     * the plan (in the topological order of the original tasks from which they originate).
     *
     * @return the new expanded tasks to be added to the plan
     */
    public Collection<TaskDescription> getTasksToAdd(){

        Vector<TaskDescription> all = new Vector<TaskDescription>();
        TaskDescription [] arr = null;

        all.addAll(this.expansions.values());

        arr = all.toArray(new TaskDescription[0]);
        Arrays.sort(arr, new TaskDescription.TopologicalComparator());

        return Arrays.asList(arr);
    }

    /**
     * Returns the result of the expansion - a list of tasks that should be removed from the
     * plan completely (as their expansions are added to the plan).
     *
     * @return the old tasks to be removed from the plan
     */
    public Collection<TaskDescription> getTasksToRemove(){

        return this.expansions.keySet();
    }


    /**
     * Expands all the "**"s in the task inputs specification.
     *
     * @todo it would be better to create a completely new task object, so that it doesn't get confusing
     */
    private void expandHere() throws TaskException {
        
        Vector<String> taskInput = this.task.getInput();
        
        for (int i = this.inputHere.size() - 1; i >= 0; --i) {

            int pos = this.inputHere.get(i);
            Vector<String> files = this.expandPattern(taskInput.get(pos), false);

            task.replaceInput(pos, files);
        }
    }

    /**
     * Expands all the "***"s in the task inputs specification.
     */
    private void expandCarthesian() throws TaskException {

        Vector<String> taskInput = this.task.getInput();

        // iterate over all possible "***" patterns and expand them one by one
        for (Integer pos : this.inputCarth) {

            Vector<String> matches = this.expandPattern(taskInput.get(pos), true);
            Vector<TaskDescription> nextExp = new Vector<TaskDescription>();

            for (String match : matches) {

                if (this.expansions.get(this.task) != null){ // some "*"'s and/or later iterations

                    for (TaskDescription t : this.expansions.removeAll(this.task)) {
                        nextExp.add(t.expand(match, pos));
                    }
                }
                else { // pure "***" and first iteration
                    nextExp.add(this.task.expand(match, pos));
                }
            }
            this.expansions.putAll(this.task, nextExp);
        }
    }

    /**
     * This expands outputs for all tasks to which the original task expanded, according to their
     * pattern replacements. It assumes the expansions of the task are already located in {@link #expansions}.
     * @param original the original, unexpanded task
     */
    private void expandOutputs(TaskDescription original) throws TaskException {

        // if there are some pattern and some non-pattern outputs, something is wrong
        if (task.getOutputPatternPos("*").size() != task.getOutput().size()){
            throw new TaskException(TaskException.ERR_PATTERN_SPECS, task.getId(),
                    "Some outputs have '*' patterns and some don't.");
        }
    
        for (TaskDescription t : this.expansions.get(original)) {
            // find out to what was the input pattern expanded
            String expPat = t.getPatternReplacement();
            Vector<String> outputs = t.getOutput();

            // copy the pattern expansion to the input
            for (int i = 0; i < outputs.size(); ++i) {
                outputs.set(i, outputs.get(i).replace("*", expPat));
            }
        }
    }


    /**
     * Tries to find patterns in input and output specifications of the task.
     */
    private void findPatterns() {

        this.inputTrans = this.task.getInputPatternPos("*");
        this.inputHere = this.task.getInputPatternPos("**");
        this.inputCarth = this.task.getInputPatternPos("***");
        this.outputTrans = this.task.getOutputPatternPos("*");
        this.outputHere = this.task.getOutputPatternPos("**");
    }

    /**
     * Expands a pattern for corresponding file names. Returns just the expansions or file names
     * as a whole. The returned list is alphabetically sorted.
     * 
     * @param pattern the pattern to be expanded
     * @param justExpansions should it return just the expansions ?
     * @return expansions or file names corresponding to the pattern
     * @throws TaskException if no files can be found for this pattern
     */
    private Vector<String> expandPattern(String pattern, boolean justExpansions) throws TaskException {

        String dirName = null, filePattern = null;
        Vector<String> ret = new Vector<String>();
        String [] files;

        // split directory and file name pattern, since findPattern recognizes only such
        // patterns that have just one or more subsequent stars in the file name, the dirName
        // must be always a valid path name, not a pattern, and the filePattern must not be empty
        if (pattern.indexOf(File.separator) != -1){
            dirName = pattern.substring(0, pattern.lastIndexOf(File.separator));
            filePattern = StringUtils.truncateFileName(pattern);
        }
        else {
            dirName = ".";
            filePattern = pattern;
        }

        filePattern = filePattern.replaceFirst("\\*+", "*"); // ensure we have just one star in the pattern
        files = new File(dirName).list();

        // no files in the directory
        if (files == null){
            throw new TaskException(TaskException.ERR_NO_FILES, this.task.getId(), "(" + pattern + ")");
        }

        // sort the list alphabetically
        Arrays.sort(files);
        // find all matching files and push the expansions or whole file names to the results list
        for (String file : files){

            String expansion;

            if ((expansion = this.matches(filePattern, file)) != null && new File(dirName + File.separator + file).isFile()){
                if (justExpansions){
                    ret.add(expansion);
                }
                else {
                    ret.add(dirName + File.separator + file);
                }
            }
        }
        // no matching files in the directory
        if (ret.isEmpty()){
            throw new TaskException(TaskException.ERR_NO_FILES, this.task.getId(), "(" + pattern + ")");
        }
        // return the result
        return ret;
    }


    /**
     * Replaces all patterns in output file names according to the expansion value of input
     * patterns, replaces patterns in dependent tasks accordingly. 
     * Calls {@link #expandDependent(TaskDescription, TaskDescription)},
     * which calls this method recursively.
     *
     * @param expTask the task whose outputs and dependencies should be expanded
     */
    private void expandOutputsAndDeps(TaskDescription expTask) throws TaskException {
        
        this.expandOutputs(expTask);
        Vector<TaskDescription> deps = expTask.getDependent();

        // expand dependent tasks, only if they have '*'-patterns (cannot expand for '**' and '***', yet)
        if (deps != null){
            for (TaskDescription dep : deps){
                if (!this.expansions.containsKey(dep) && dep.hasInputPattern("*")){
                    this.expandDependent(expTask, dep);
                }
            }
        }
    }


    /**
     * Expands all dependent tasks, given an expanded task and an unexpanded one, which depends on it.
     * @param anc the already expanded task
     * @param task its dependent, not yet expanded task
     * @throws DataException if there are some problems with the tasks specifications
     */
    private void expandDependent(TaskDescription anc, TaskDescription task) 
            throws TaskException {

        // this means there are no more dependent expansions and we need only to put all outputs
        // from expanded anc as inputs to this task
        if (!task.hasOutputPattern("*") && !task.hasOutputPattern("**")){

            Vector<String> replacements = new Vector<String>();
            Vector<String> taskInput = task.getInput();
            Vector<Integer> patterns = task.getInputPatternPos("*");

            for (TaskDescription ancExp : this.expansions.get(anc)){ // find pattern replacements
                replacements.add(ancExp.getPatternReplacement());
            }
            for (int i = patterns.size() - 1; i >= 0; --i){ // apply them in inputs

                Vector<String> files = new Vector<String>(replacements.size());
                String pattern = taskInput.get(patterns.get(i));

                for (String rep : replacements){ // all replacements for each "*"-input
                    files.add(pattern.replace("*", rep));
                }

                task.replaceInput(patterns.get(i), files);
            }
            return;
        }

        // now we know the expansion line continues, we need to expand this task
        
        // expand the "task", according to the expansions of anc
        for (TaskDescription ancExp : this.expansions.get(anc)){
            
            TaskDescription expanded = task.expand(ancExp.getPatternReplacement()); // this expands "*"s

            this.cleanPrerequisities(expanded);
            this.expansions.put(task, expanded);
        }

        // if there are "**" or "***" left to be expanded, we can't expand outputs and go deeper, yet
        if (task.hasOutputPattern("**") || task.hasInputPattern("**") || task.hasInputPattern("***")){
            return;
        }

        // now continue to outputs and dependent tasks expansion
        this.expandOutputsAndDeps(task);
    }

    /**
     * Matches a file name pattern against a real file name. Only patterns
     * with just one single "*" are supported. Returns null or the expansion of the pattern.
     *
     * @param pattern the pattern (see detailed method description for restrictions)
     * @param fileName the file name to match against the pattern
     * @return the expansion of the pattern if successful, null otherwise
     */
    private String matches(String pattern, String fileName) {

        String beg = pattern.substring(0, pattern.indexOf("*"));
        String end = pattern.endsWith("*") ? "" : pattern.substring(pattern.indexOf("*") + 1);

        if (fileName.startsWith(beg) && fileName.endsWith(end)
                && fileName.length() >= beg.length() + end.length()){
            return fileName.substring(beg.length(), fileName.length() - end.length());
        }
        return null;
    }

    /**
     * This removes all the unnecessary expansions of prerequisites of the given task. E.g. if
     * task2#a is an expanded version of task2 which depended on task1, and there exist expansions
     * task1#a and task1#b, by default task2#a depends on both of them. This removes task1#b from
     * the dependency list.
     *
     * @todo optimize -- this is really expensive (creates a hash-set every time)
     * @param expTas the task to be processed
     */
    private void cleanPrerequisities(TaskDescription expTask) {

        HashSet<TaskDescription> values = new HashSet<TaskDescription>(this.expansions.values());

        for (TaskDescription pre : expTask.getPrerequisites()){
            if (values.contains(pre)
                    && !expTask.getPatternReplacement().equals(pre.getPatternReplacement())){
                pre.removeDependency(expTask);
            }
        }
    }

}