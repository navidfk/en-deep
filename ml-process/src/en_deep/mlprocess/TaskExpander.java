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

import en_deep.mlprocess.exception.DataException;
import en_deep.mlprocess.exception.TaskException;
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
    private Vector<Integer> outputTrans;

    /** All the tasks to be removed from the plan */
    private Vector<TaskDescription> remove;
    /** All the newly created tasks to be added to the plan */
    private Vector<TaskDescription> add;


    /* METHODS */

    /**
     * This creates a new {@link TaskExpander} object, just setting the {@link TaskDescription} to be
     * processed. The expansion process is triggered by {@link expand()}
     *
     * @param task the task to be expanded
     */
    public TaskExpander(TaskDescription task){

        this.task = task;
        this.remove = new Vector<TaskDescription>();
        this.add = new Vector<TaskDescription>();

        this.inputTrans = null;
        this.inputHere = null;
        this.inputCarth = null;

        this.outputTrans = null;
    }


    /**
     * The actual expansion process. When it's finished, the new tasks and tasks to be deleted may be
     * retrieved using {@link getTasksToRemove()} and {@link getTasksToAdd()}
     *
     * @throws DataException if there's a problem with the data specification
     */
    public void expand() throws TaskException {

        this.findPatterns();

        // there are no patterns or just "**"s
        if (this.inputTrans == null && this.inputCarth == null){

            // can't have "*"-patterns in outputs since there are none in inputs
            if (this.outputTrans != null){
               throw new TaskException(TaskException.ERR_PATTERN_SPECS, this.task.getId());
            }
            // expand "**" here if needed
            if (this.inputHere != null){
                this.expandHere();
            }
            return;
        }
        // "**" are not compatible with other types
        else if (this.inputHere != null){
            throw new TaskException(TaskException.ERR_PATTERN_SPECS, Process.getInstance().getInputFile());
        }
        // if there are "*"s, expand them all at a time
        else if (this.inputTrans != null){

            Vector<String> taskInput = this.task.getInput();
            Vector<String> matches = this.expandPattern(taskInput.get(this.inputTrans.firstElement()), true);

            for (String match : matches){
                this.add.add(this.task.expand(match));
            }
        }
        // there are only "***" -- prepare for their processing
        else {
            this.add.add(this.task);
        }

        // expand "***"s -- one by one
        if (this.inputCarth != null){
            expandCarthesian();
        }

        // check if all the outputs have "*"s
        if (this.outputTrans.size() != task.getOutput().size()){
            throw new TaskException(TaskException.ERR_PATTERN_SPECS, this.task.getId());
        }

        // expand outputs and dependent tasks using the expanded task name, select tasks for removal
        this.expandOutputsAndDeps(this.add);

    }


    /**
     * Returns the result of the expansion - a list of new expanded tasks that should be added to
     * the plan.
     * @return
     */
    public Vector<TaskDescription> getTasksToAdd(){
        return this.add;
    }

    /**
     * Returns the result of the expansion - a list of tasks that should be removed from the
     * plan completely (as their expansions are added to the plan).
     */
    public Vector<TaskDescription> getTasksToRemove(){
        return this.remove;
    }


    /**
     * Expands all the "**"s in the task inputs specification.
     */
    private void expandHere() {
        
        Vector<String> taskInput = this.task.getInput();
        
        for (int i = this.inputHere.size() - 1; i <= 0; ++i) {

            int pos = this.inputHere.get(i);
            Vector<String> files = this.expandPattern(taskInput.get(pos), false);

            taskInput.remove(i);
            taskInput.addAll(i, files);
        }
    }

    /**
     * Expands all the "***"s in the task inputs specification.
     */
    private void expandCarthesian() {

        Vector<String> taskInput = this.task.getInput();

        for (Integer pos : this.inputCarth) {

            Vector<String> matches = this.expandPattern(taskInput.get(this.inputCarth.get(pos)), true);
            Vector<TaskDescription> nextExp = new Vector<TaskDescription>();

            for (String match : matches) {
                for (TaskDescription t : this.add) {
                    nextExp.add(t.expand(match, pos));
                }
            }
            this.add = nextExp;
        }
    }


    /**
     * Tries to find patterns in input and output specifications of the task.
     */
    private void findPatterns() {

        this.inputTrans = this.findPattern("*", this.task.getInput());
        this.inputHere = this.findPattern("**", this.task.getInput());
        this.inputCarth = this.findPattern("***", this.task.getInput());
        this.outputTrans = this.findPattern("*", this.task.getOutput());
    }

    /**
     * Expands a pattern for corresponding file names. Returns just the expansions or file names
     * as a whole.
     * @param pattern the pattern to be expanded
     * @param justExpansions should it return just the expansions ?
     * @return expansions or file names corresponding to the pattern
     */
    private Vector<String> expandPattern(String pattern, boolean justExpansions) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    /**
     * Tries to search for one pattern within a given field. Only such strings are returned in which
     * the pattern is found only once.
     * @param pattern the pattern to search for
     * @param field the field to search within
     * @return list of indexes at which the pattern was found, or null if none such exist
     */
    private Vector<Integer> findPattern(String pattern, Vector<String> field) {

        Vector<Integer> ret = null;

        for (int i = 0; i < field.size(); ++i){
            String elem = field.get(i);
            // ensure we don't return ** as * etc. -- TODO check for multiple patterns in one string ?
            if (elem.indexOf(pattern) != -1 && elem.indexOf(pattern) == elem.lastIndexOf(pattern)){
                if (ret == null){
                    ret = new Vector<Integer>();
                }
                ret.add(i);
            }
        }
        return ret;
    }

    /**
     * This behaves exactly same as {@link findPattern}, but returns only after just one given pattern has been found.
     * @param pattern the pattern to search for
     * @param field the field to search within
     * @return list of indexes at which the pattern was found, or null if none such exist
     */
    private boolean hasPattern(String pattern, Vector<String> field) {

        for (int i = 0; i < field.size(); ++i){
            String elem = field.get(i);
            // ensure we don't return ** as * etc. -- TODO check for multiple patterns in one string ?
            if (elem.indexOf(pattern) != -1 && elem.indexOf(pattern) == elem.lastIndexOf(pattern)){
                return true;
            }
        }
        return false;
    }


    /**
     * Replaces all patterns in output file names according to the expansion value of input
     * patterns, replaces patterns in dependent tasks accordingly and selects the unexpanded tasks for removal.
     *
     * @param tasks the tasks to be processed.
     */
    private void expandOutputsAndDeps(Vector<TaskDescription> tasks) throws TaskException {

        this.remove.add(this.task); // remove the original task
        boolean removeOrig = false; // remove the dependent unexpanded tasks just once

        for (TaskDescription t : tasks){
            // find out to what was the input pattern expanded
            String expPat = t.getId().substring(t.getId().indexOf('#')).replaceAll("#", "_");
            Vector<String> outputs = t.getOutput();

            for (int i = 0; i < outputs.size(); ++i){
                outputs.set(i, outputs.get(i).replace("*", expPat));
            }

            Vector<TaskDescription> deps = t.getDependent();
            for (TaskDescription dep : deps){
                this.expandDependent(t, dep, expPat, removeOrig);
            }
            removeOrig = true;
        }
    }


    /**
     * Expands all dependent tasks with the given pattern, given an expanded one and an unexpanded one.
     * @param anc the already expanded task
     * @param task its dependent, not yet expanded task
     * @param expPat the expansion pattern
     * @param removeOriginal should we add the original tasks for removal?
     * @throws DataException if there are some problems with the tasks specifications
     */
    private void expandDependent(TaskDescription anc, TaskDescription task, String expPat, boolean removeOriginal) 
            throws TaskException {

        TaskDescription expanded = task.expand(expPat); // this expands "*"s

        if (removeOriginal){
            this.remove.add(task);
        }
        this.add.add(expanded);
        expanded.looseDeps(anc.getId().substring(0, anc.getId().indexOf('#')));
        expanded.setDependency(anc);

        // if there are "**", something is wrong
        if (this.hasPattern("**", expanded.getInput())){
            throw new TaskException(TaskException.ERR_PATTERN_SPECS, task.getId());
        }
        // if there are "***", we can't expand outputs and cannot go deeper
        if (this.hasPattern("***", expanded.getInput())){
            return;
        }

        // expand outputs
        Vector<String> outputs = task.getOutput();

        // if there are non-pattern outputs, something is wrong
        if (this.findPattern("*", outputs).size() != outputs.size()){ 
            throw new TaskException(TaskException.ERR_PATTERN_SPECS, task.getId());
        }

        for (int i = 0; i < outputs.size(); ++i){
            outputs.set(i, outputs.get(i).replace("*", expPat));
        }

        // go deeper only if we have "*" ("**" and pure-"***" cannot be expanded yet)
        Vector<TaskDescription> deps = expanded.getDependent();

        for (TaskDescription dep : deps){

            if (this.hasPattern("*", dep.getInput())){
                this.expandDependent(expanded, dep, expPat, removeOriginal);
            }
        }

    }

}
