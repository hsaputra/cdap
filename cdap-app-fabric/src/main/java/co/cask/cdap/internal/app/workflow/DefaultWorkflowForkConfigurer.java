/*
 * Copyright © 2015 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package co.cask.cdap.internal.app.workflow;

import co.cask.cdap.api.Predicate;
import co.cask.cdap.api.schedule.SchedulableProgramType;
import co.cask.cdap.api.workflow.WorkflowAction;
import co.cask.cdap.api.workflow.WorkflowActionSpecification;
import co.cask.cdap.api.workflow.WorkflowConditionConfigurer;
import co.cask.cdap.api.workflow.WorkflowConditionNode;
import co.cask.cdap.api.workflow.WorkflowContext;
import co.cask.cdap.api.workflow.WorkflowForkConfigurer;
import co.cask.cdap.api.workflow.WorkflowForkNode;
import co.cask.cdap.api.workflow.WorkflowNode;
import co.cask.cdap.internal.workflow.DefaultWorkflowActionSpecification;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

import java.util.List;

/**
 * Default implementation of the {@link WorkflowForkConfigurer}
 * @param <T>
 */
public class DefaultWorkflowForkConfigurer<T extends WorkflowForkJoiner & WorkflowConditionAdder>
  implements WorkflowForkConfigurer<T>, WorkflowForkJoiner, WorkflowConditionAdder {

  private final T parentForkConfigurer;

  private final List<List<WorkflowNode>> branches = Lists.newArrayList();
  private List<WorkflowNode> currentBranch;

  public DefaultWorkflowForkConfigurer(T parentForkConfigurer) {
    this.parentForkConfigurer = parentForkConfigurer;
    currentBranch = Lists.newArrayList();
  }

  @Override
  public WorkflowForkConfigurer<T> addMapReduce(String mapReduce) {
    currentBranch.add(WorkflowNodeCreator.createWorkflowActionNode(mapReduce, mapReduce,
                                                                   SchedulableProgramType.MAPREDUCE));
    return this;
  }

  @Override
  public WorkflowForkConfigurer<T> addMapReduce(String uniqueName, String mapReduce) {
    currentBranch.add(WorkflowNodeCreator.createWorkflowActionNode(uniqueName, mapReduce,
                                                                   SchedulableProgramType.MAPREDUCE));
    return this;
  }

  @Override
  public WorkflowForkConfigurer<T> addSpark(String spark) {
    currentBranch.add(WorkflowNodeCreator.createWorkflowActionNode(spark, spark, SchedulableProgramType.SPARK));
    return this;
  }

  @Override
  public WorkflowForkConfigurer<T> addSpark(String uniqueName, String spark) {
    currentBranch.add(WorkflowNodeCreator.createWorkflowActionNode(uniqueName, spark, SchedulableProgramType.SPARK));
    return this;
  }

  @Override
  public WorkflowForkConfigurer<T> addAction(WorkflowAction action) {
    Preconditions.checkArgument(action != null, "WorkflowAction is null.");
    WorkflowActionSpecification spec = new DefaultWorkflowActionSpecification(action);
    currentBranch.add(WorkflowNodeCreator.createWorkflowCustomActionNode(spec.getName(), spec));
    return this;
  }

  @Override
  public WorkflowForkConfigurer<T> addAction(String uniqueName, WorkflowAction action) {
    Preconditions.checkArgument(action != null, "WorkflowAction is null.");
    WorkflowActionSpecification spec = new DefaultWorkflowActionSpecification(action);
    currentBranch.add(WorkflowNodeCreator.createWorkflowCustomActionNode(uniqueName, spec));
    return this;
  }

  @Override
  @SuppressWarnings("unchecked")
  public WorkflowForkConfigurer<? extends WorkflowForkConfigurer<T>> fork() {
    return new DefaultWorkflowForkConfigurer<>(this);
  }

  @Override
  public WorkflowConditionConfigurer<? extends WorkflowForkConfigurer<T>> condition(
    Predicate<WorkflowContext> predicate) {
    String predicateClassName = predicate.getClass().getName();
    return new DefaultWorkflowConditionConfigurer<>(predicate.getClass().getSimpleName(), this, predicateClassName);
  }

  @Override
  public WorkflowConditionConfigurer<? extends WorkflowForkConfigurer<T>> condition(
    String uniqueName, Predicate<WorkflowContext> predicate) {
    String predicateClassName = predicate.getClass().getName();
    return new DefaultWorkflowConditionConfigurer<>(uniqueName, this, predicateClassName);
  }

  @Override
  public WorkflowForkConfigurer<T> also() {
    branches.add(currentBranch);
    currentBranch = Lists.newArrayList();
    return this;
  }

  @Override
  @SuppressWarnings("unchecked")
  public T join() {
    branches.add(currentBranch);
    parentForkConfigurer.addWorkflowForkNode(branches);
    return parentForkConfigurer;
  }

  @Override
  public void addWorkflowForkNode(List<List<WorkflowNode>> branches) {
    currentBranch.add(new WorkflowForkNode(null, branches));
  }

  @Override
  public void addWorkflowConditionNode(String uniqueName, String predicateClassName, List<WorkflowNode> ifBranch,
                                       List<WorkflowNode> elseBranch) {
    currentBranch.add(new WorkflowConditionNode(uniqueName, predicateClassName, ifBranch, elseBranch));
  }
}
