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

package org.jbpm.process.core.context.variable;

import java.util.List;

import org.jbpm.workflow.core.node.DataAssociation;

public interface Mappable {

    DataAssociation addInMapping(String parameterName, String variableName);
    
    DataAssociation addOutMapping(String parameterName, String variableName);

    List<DataAssociation> getInMappings();
    
    List<DataAssociation> getOutMappings();
}
