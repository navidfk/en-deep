/*
 *  Copyright (c) 2009 Ondrej Dusek
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

import en_deep.mlprocess.Task.TaskStatus;
import en_deep.mlprocess.Task.TaskType;
import java.io.Serializable;
import java.util.Vector;

/**
 *
 * @author Ondrej Dusek
 */
public abstract class TaskDescription implements Serializable {

    /* CONSTANTS */
    
    
    /* DATA */

    /** The type of this task */
    private TaskType type;
    /** The task global ID */
    private String id;
    /** The algorithm used for this task */
    private AlgorithmDescription algorithm;
    /** The current task status */
    private TaskStatus status;

    /** Topological order of the task (-1 if not sorted) */
    private int topolOrder;

    /** All the Tasks that this Task depends on */
    private Vector<TaskDescription> iDependOn;
    /** All the Task that are depending on this one */
    private Vector<TaskDescription> dependOnMe;

    /** This serves for generating tasks id in parallelization */
    private static int lastId = 0;


    /* METHODS */

    /**
     * Creates a new TaskDescription, should be used only in the constructors
     * of derived classes {@link ComputationDescription}, {@link ManipulationDescription}
     * and {@link EvaluationDescription}.
     *
     * @param type the type of this task.
     */
    protected TaskDescription(TaskType type, String id, AlgorithmDescription algorithm){

        this.type = type;
        this.id = TaskDescription.generateId(id);
        this.algorithm = algorithm;
        this.status = TaskStatus.PENDING; // no dependencies, yet
        this.topolOrder = -1; // not yet sorted
    }

    /**
     * Returns all the output files/data sets/features of this {@link Task}.
     * @return the set-up input
     */
    public abstract Vector<DataSourceDescription> getInputDataSources();

    /**
     * Returns all the output files/data sets/features of this {@link Task}.
     * @return the set-up output
     */
    public abstract Vector<DataSourceDescription> getOutputDataSources();


    /**
     * Sets a dependency for this task (i.e\. marks this {@link TaskDescription} as depending
     * on the parameter). Checks for duplicate dependencies, i.e. a dependency from task A to
     * task B is stored only once, even if it is enforced multiple times. Sets the task status
     * to waiting - the dependent task needs to be processed first.
     *
     * @param source the governing {@link TaskDescription} that must be processed before this one.
     */
    void setDependency(TaskDescription source) {

        // if we have a dependency, we need to wait for it to finish
        this.status = TaskStatus.WAITING;

        if (this.iDependOn == null){
            this.iDependOn = new Vector<TaskDescription>();
        }
        if (!this.iDependOn.contains(source)){
            this.iDependOn.add(source);
        }

        if (source.dependOnMe == null){
            source.dependOnMe = new Vector<TaskDescription>();
        }
        if (!source.dependOnMe.contains(this)){
            source.dependOnMe.add(this);
        }
    }

    /**
     * Returns the current task progress status.
     * @return the current task status
     */
    public TaskStatus getStatus(){
        return this.status;
    }

    /**
     * Returns the task topological order (zero-based), or -1 if not yet sorted.
     * @return the topological order of the task
     */
    public int getOrder(){
        return this.topolOrder;
    }

    /**
     * Sets the task's topological order (as done in task topological sorting in
     * {@link Plan.sortPlan(Vector<TaskDescription> Plan)}).
     *
     * @param order the topological order for the task
     */
    void setOrder(int order){
        this.topolOrder = order;
    }

    /**
     * Returns a list of all dependent tasks, or null if there are none.
     * @return a list of all tasks depending on this one
     */
    Vector<TaskDescription> getDependent(){

        if (this.dependOnMe == null){
            return null;
        }
        return (Vector<TaskDescription>) this.dependOnMe.clone();
    }


    /**
     * Returns the unique ID for this task, composed of the name for the task
     * section in the input scenario file and a unique number.
     *
     * @return the generated ID string
     */
    private static synchronized String generateId(String prefix){

        String taskId;

        lastId++;
        taskId = prefix + lastId;

        return taskId;
    }

    /**
     * Returns true if all the tasks on which this task depends are already topologically
     * sorted, i.e. their {@link topolOrder} is >= 0. If we have no prerequisities, "all of
     * them are sorted".
     *
     * @return the sorting status of the prerequisities tasks
     */
    boolean allPrerequisitiesSorted() {

        if (this.iDependOn == null){ // if there are none, they're all sorted.
            return true;
        }
        for (TaskDescription prerequisity : this.iDependOn){
            if (prerequisity.getOrder() < 0){
                return false;
            }
        }
        return true;
    }

    /**
     * Returns the task's {@link AlgorithmDescription}.
     * @return the description of the algorithm for this task
     */
    AlgorithmDescription getAlgorithm() {
        return this.algorithm;
    }

    /**
     * Returns the ID of this task.
     * @return the task's id.
     */
    String getId(){
        return this.id;
    }

    /**
     * Returns the type of this task (according to {@link Task.TaskType}.
     *
     * @return the task type
     */
    TaskType getType() {
        return this.type;
    }

}