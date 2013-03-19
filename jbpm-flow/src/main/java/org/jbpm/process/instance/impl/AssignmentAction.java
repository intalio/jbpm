/**
 * Copyright 2010 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jbpm.process.instance.impl;

import java.util.Map;
import org.kie.api.runtime.process.ProcessContext;
import org.drools.core.process.instance.WorkItem;

public interface AssignmentAction {
    
	public static String START_MESSAGE = "startMesage";
	public static final String ASSIGNMENT_ACTION = "assignmentAction";
	
    void execute(WorkItem workItem, ProcessContext context) throws Exception;
    
    void execute(Map<String,Object> metadata, ProcessContext context) throws Exception;
}
