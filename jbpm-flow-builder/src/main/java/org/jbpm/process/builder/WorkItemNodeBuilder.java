package org.jbpm.process.builder;

import java.util.List;

import org.kie.api.definition.process.Node;
import org.kie.api.definition.process.Process;
import org.drools.compiler.lang.descr.ProcessDescr;
import org.jbpm.process.builder.dialect.ProcessDialect;
import org.jbpm.process.builder.dialect.ProcessDialectRegistry;
import org.jbpm.workflow.core.node.Assignment;
import org.jbpm.workflow.core.node.DataAssociation;
import org.jbpm.workflow.core.node.WorkItemNode;

public class WorkItemNodeBuilder extends EventBasedNodeBuilder {

	public void build(Process process, ProcessDescr processDescr,
			ProcessBuildContext context, Node node) {
		super.build(process, processDescr, context, node);
		for (DataAssociation dataAssociation: ((WorkItemNode) node).getInAssociations()) {
			List<Assignment> assignments = dataAssociation.getAssignments();
			if (assignments != null) {
				for (Assignment assignment: assignments) {
					ProcessDialect dialect = ProcessDialectRegistry.getDialect( assignment.getDialect() );            
			    	dialect.getAssignmentBuilder().build( 
		    			context, assignment, 
		    			dataAssociation.getSources().get(0), 
		    			dataAssociation.getTarget(), 
		    			((WorkItemNode) node), true);
				}
			}
		}
		for (DataAssociation dataAssociation: ((WorkItemNode) node).getOutAssociations()) {
			List<Assignment> assignments = dataAssociation.getAssignments();
			if (assignments != null) {
				for (Assignment assignment: assignments) {
					ProcessDialect dialect = ProcessDialectRegistry.getDialect( assignment.getDialect() );
					String source = null;
					if(dataAssociation.getSources().size() > 0) {
						source = dataAssociation.getSources().get(0);
					}
			    	dialect.getAssignmentBuilder().build(
		    			context, assignment, 
		    			source, 
		    			dataAssociation.getTarget(),
		    			((WorkItemNode) node), false);
				}
			}
		}
	}

}
