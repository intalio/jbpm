package org.jbpm.process.instance;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.drools.RuleBase;
import org.drools.SessionConfiguration;
import org.drools.common.AbstractWorkingMemory;
import org.drools.common.InternalKnowledgeRuntime;
import org.drools.common.InternalRuleBase;
import org.drools.event.ProcessEventSupport;
import org.drools.impl.InternalKnowledgeBase;
import org.drools.rule.Rule;
import org.drools.time.AcceptsTimerJobFactoryManager;
import org.drools.time.impl.DefaultTimerJobFactoryManager;
import org.drools.time.impl.TrackableTimeJobFactoryManager;
import org.jbpm.process.core.event.EventFilter;
import org.jbpm.process.core.event.EventTypeFilter;
import org.jbpm.process.instance.event.SignalManager;
import org.jbpm.process.instance.event.SignalManagerFactory;
import org.jbpm.process.instance.impl.AssignmentAction;
import org.jbpm.process.instance.timer.TimerManager;
import org.jbpm.ruleflow.core.RuleFlowProcess;
import org.jbpm.workflow.core.node.EventTrigger;
import org.jbpm.workflow.core.node.StartNode;
import org.jbpm.workflow.core.node.Trigger;
import org.kie.definition.process.Node;
import org.kie.definition.process.Process;
import org.kie.event.kiebase.AfterProcessAddedEvent;
import org.kie.event.kiebase.AfterProcessRemovedEvent;
import org.kie.event.kiebase.DefaultKieBaseEventListener;
import org.kie.event.process.ProcessEventListener;
import org.kie.event.rule.DefaultAgendaEventListener;
import org.kie.event.rule.MatchCreatedEvent;
import org.kie.event.rule.RuleFlowGroupDeactivatedEvent;
import org.kie.internal.utils.CompositeClassLoader;
import org.kie.process.CorrelationKey;
import org.kie.runtime.StatefulKnowledgeSession;
import org.kie.runtime.process.EventListener;
import org.kie.runtime.process.ProcessInstance;
import org.kie.runtime.process.WorkItemManager;

public class ProcessRuntimeImpl implements InternalProcessRuntime {
	
	private AbstractWorkingMemory workingMemory;
	private InternalKnowledgeRuntime kruntime;
	
	private ProcessInstanceManager processInstanceManager;
	private SignalManager signalManager;
	private TimerManager timerManager;
	private ProcessEventSupport processEventSupport;
	private DefaultKieBaseEventListener knowledgeBaseListener;

	public ProcessRuntimeImpl(InternalKnowledgeRuntime kruntime) {
		this.kruntime = kruntime;
        AcceptsTimerJobFactoryManager jfm = ( AcceptsTimerJobFactoryManager ) kruntime.getTimerService();
        if ( jfm.getTimerJobFactoryManager() instanceof DefaultTimerJobFactoryManager ) {
            jfm.setTimerJobFactoryManager( new TrackableTimeJobFactoryManager() );
        }		
		((AcceptsTimerJobFactoryManager)kruntime.getTimerService()).setTimerJobFactoryManager( new TrackableTimeJobFactoryManager() );		
		((CompositeClassLoader) getRootClassLoader()).addClassLoader( getClass().getClassLoader() );
		initProcessInstanceManager();
		initSignalManager();
		timerManager = new TimerManager(kruntime, kruntime.getTimerService());
        processEventSupport = new ProcessEventSupport();
        initProcessEventListeners();
        initProcessActivationListener();        
	}
	
	public ProcessRuntimeImpl(AbstractWorkingMemory workingMemory) {
		this.workingMemory = workingMemory;
		AcceptsTimerJobFactoryManager jfm = ( AcceptsTimerJobFactoryManager ) workingMemory.getTimerService();
		if ( jfm.getTimerJobFactoryManager() instanceof DefaultTimerJobFactoryManager ) {
		    jfm.setTimerJobFactoryManager( new TrackableTimeJobFactoryManager() );
		}
		
		this.kruntime = (InternalKnowledgeRuntime) workingMemory.getKnowledgeRuntime();
		((CompositeClassLoader) getRootClassLoader()).addClassLoader( getClass().getClassLoader() );
		initProcessInstanceManager();
		initSignalManager();
		timerManager = new TimerManager(kruntime, kruntime.getTimerService());
        processEventSupport = new ProcessEventSupport();
        initProcessEventListeners();
        initProcessActivationListener();
	}
	
	private void initProcessInstanceManager() {
		String processInstanceManagerClass = ((SessionConfiguration) kruntime.getSessionConfiguration()).getProcessInstanceManagerFactory();
		try {
			processInstanceManager = 
				((ProcessInstanceManagerFactory) loadClass(processInstanceManagerClass).newInstance())
			        .createProcessInstanceManager(kruntime);
		} catch (InstantiationException e) {
			throw new RuntimeException(e);
		} catch (IllegalAccessException e) {
			throw new RuntimeException(e);
		}
	}
	
	private void initSignalManager() {
		String signalManagerClass = ((SessionConfiguration) kruntime.getSessionConfiguration()).getSignalManagerFactory();
		try {
			signalManager = ((SignalManagerFactory) loadClass(signalManagerClass).newInstance())
		        .createSignalManager(kruntime);
		} catch (InstantiationException e) {
			throw new RuntimeException(e);
		} catch (IllegalAccessException e) {
			throw new RuntimeException(e);
		}
	}
	
	private Class<?> loadClass(String className) {
	    try {
            return getRootClassLoader().loadClass(className);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
	}
	
	private ClassLoader getRootClassLoader() {
		RuleBase ruleBase = ((InternalKnowledgeBase) kruntime.getKieBase()).getRuleBase();
		if (ruleBase != null) {
			return ((InternalRuleBase) ((InternalKnowledgeBase) kruntime.getKieBase()).getRuleBase()).getRootClassLoader();
		}
		CompositeClassLoader result = new CompositeClassLoader();
		result.addClassLoader(this.getClass().getClassLoader());
		return result;
	}
	
    public ProcessInstance startProcess(final String processId) {
        return startProcess(processId, null);
    }

    public ProcessInstance startProcess(String processId,
                                        Map<String, Object> parameters) {
    	ProcessInstance processInstance = createProcessInstance(processId, parameters);
        if ( processInstance != null ) {
            // start process instance
        	return startProcessInstance(processInstance.getId());
        }
        return null;
    }
    
    public ProcessInstance createProcessInstance(String processId,
                                                 Map<String, Object> parameters) {
        return createProcessInstance(processId, null, parameters);
    }
    
    public ProcessInstance startProcessInstance(long processInstanceId) {
        try {
            kruntime.startOperation();
            if ( !kruntime.getActionQueue().isEmpty() ) {
            	kruntime.executeQueuedActions();
            }
            ProcessInstance processInstance = getProcessInstance(processInstanceId);
	        getProcessEventSupport().fireBeforeProcessStarted( processInstance, kruntime );
	        ((org.jbpm.process.instance.ProcessInstance) processInstance).start();
	        getProcessEventSupport().fireAfterProcessStarted( processInstance, kruntime );
	        return processInstance;
        } finally {
        	kruntime.endOperation();
        }
    }
    
    @Override
    public ProcessInstance startProcess(String processId,
            CorrelationKey correlationKey, Map<String, Object> parameters) {
        ProcessInstance processInstance = createProcessInstance(processId, correlationKey, parameters);
        if ( processInstance != null ) {
            return startProcessInstance(processInstance.getId());
        }
        return null;
    }

    @Override
    public ProcessInstance createProcessInstance(String processId,
            CorrelationKey correlationKey, Map<String, Object> parameters) {
        try {
            kruntime.startOperation();
            if ( !kruntime.getActionQueue().isEmpty() ) {
                kruntime.executeQueuedActions();
            }
            final Process process = kruntime.getKieBase().getProcess( processId );
            if ( process == null ) {
                throw new IllegalArgumentException( "Unknown process ID: " + processId );
            }
            return startProcess( process, correlationKey, parameters );
        } finally {
            kruntime.endOperation();
        }
    }

    @Override
    public ProcessInstance getProcessInstance(CorrelationKey correlationKey) {

        return processInstanceManager.getProcessInstance(correlationKey);
    }

    private org.jbpm.process.instance.ProcessInstance startProcess(final Process process, CorrelationKey correlationKey,
                                         Map<String, Object> parameters) {
        ProcessInstanceFactory conf = ProcessInstanceFactoryRegistry.INSTANCE.getProcessInstanceFactory( process );
        if ( conf == null ) {
            throw new IllegalArgumentException( "Illegal process type: " + process.getClass() );
        }
        return conf.createProcessInstance( process,
                                           correlationKey,
        								   kruntime,
                                           parameters );
    }

    public ProcessInstanceManager getProcessInstanceManager() {
        return processInstanceManager;
    }
    
    public TimerManager getTimerManager() {
    	return timerManager;
    }
    
    public SignalManager getSignalManager() {
    	return signalManager;
    }

    public Collection<ProcessInstance> getProcessInstances() {
        return processInstanceManager.getProcessInstances();
    }

    public ProcessInstance getProcessInstance(long id) {
        return getProcessInstance( id, false );
    }

    public ProcessInstance getProcessInstance(long id, boolean readOnly) {
        return processInstanceManager.getProcessInstance( id, readOnly );
    }

    public void removeProcessInstance(ProcessInstance processInstance) {
        processInstanceManager.removeProcessInstance( processInstance );
    }
    
    private void initProcessEventListeners() {
        for ( Process process : kruntime.getKieBase().getProcesses() ) {
            initProcessEventListener(process);
        }
        knowledgeBaseListener = new DefaultKieBaseEventListener() {
        	@Override
        	public void afterProcessAdded(AfterProcessAddedEvent event) {
        		initProcessEventListener(event.getProcess());
        	}
        	@Override
        	public void afterProcessRemoved(AfterProcessRemovedEvent event) {
        		if (event.getProcess() instanceof RuleFlowProcess) {
        			String type = (String)
    				    ((RuleFlowProcess) event.getProcess()).getMetaData().get("StartProcessEventType");
        			StartProcessEventListener listener = (StartProcessEventListener)
        				((RuleFlowProcess) event.getProcess()).getMetaData().get("StartProcessEventListener");
        			if (type != null && listener != null) {
        				signalManager.removeEventListener(type, listener);
        			}
        		}
        	}
		};
        kruntime.getKieBase().addEventListener(knowledgeBaseListener);
    }
    
    @SuppressWarnings({ "deprecation", "unchecked" })
	private void initProcessEventListener(Process process) {
    	if ( process instanceof RuleFlowProcess ) {
            Node startNode = ((RuleFlowProcess) process).getStart();
            if (startNode != null && startNode instanceof StartNode) {
                List<Trigger> triggers = ((StartNode) startNode).getTriggers();
                if ( triggers != null ) {
                    for ( Trigger trigger : triggers ) {
                        if ( trigger instanceof EventTrigger ) {
                            final List<EventFilter> filters = ((EventTrigger) trigger).getEventFilters();
                            String type = null;
                            for ( EventFilter filter : filters ) {
                                if ( filter instanceof EventTypeFilter ) {
                                    type = ((EventTypeFilter) filter).getType();
                                }
                            }
                            StartProcessEventListener listener = new StartProcessEventListener( process.getId(),
                                                                                                filters,
                                                                                                trigger.getInMappings(),
                                                                                                (List<AssignmentAction>) startNode.getMetaData().get(AssignmentAction.ASSIGNMENT_ACTION),
                                                                                                this);
                            signalManager.addEventListener( type,
                                                            listener );
                            ((RuleFlowProcess) process).getMetaData().put("StartProcessEventType", type);
                            ((RuleFlowProcess) process).getMetaData().put("StartProcessEventListener", listener);
                        }
                    }
        	    }
        	}
        }
    }
    
    public ProcessEventSupport getProcessEventSupport() {
    	return processEventSupport;
    }

    public void addEventListener(final ProcessEventListener listener) {
        this.processEventSupport.addEventListener( listener );
    }

    public void removeEventListener(final ProcessEventListener listener) {
        this.processEventSupport.removeEventListener( listener );
    }

    public List<ProcessEventListener> getProcessEventListeners() {
        return processEventSupport.getEventListeners();
    }

    private void initProcessActivationListener() {
    	kruntime.addEventListener(new DefaultAgendaEventListener() {
			public void matchCreated(MatchCreatedEvent event) {
                String ruleFlowGroup = ((Rule) event.getMatch().getRule()).getRuleFlowGroup();
                if ( "DROOLS_SYSTEM".equals( ruleFlowGroup ) ) {
                    // new activations of the rule associate with a state node
                    // signal process instances of that state node
                    String ruleName = event.getMatch().getRule().getName();
                    if ( ruleName.startsWith( "RuleFlowStateNode-" )) {
                        int index = ruleName.indexOf( "-",
                                                      18 );
                        index = ruleName.indexOf( "-",
                                                  index + 1 );
                        String eventType = ruleName.substring( 0,
                                                               index );
                        signalManager.signalEvent( eventType,
                                                   event );
                    } else if (ruleName.startsWith( "RuleFlowStateEventSubProcess-" ) || ruleName.startsWith( "RuleFlowStateEvent-" )) {
                        signalManager.signalEvent( ruleName,  event );
                    }
                }
			}
    	});

        kruntime.addEventListener(new DefaultAgendaEventListener() {
            public void afterRuleFlowGroupDeactivated(final RuleFlowGroupDeactivatedEvent event) {
            	if (kruntime instanceof StatefulKnowledgeSession) {
                    signalManager.signalEvent( "RuleFlowGroup_" + event.getRuleFlowGroup().getName() + "_" + ((StatefulKnowledgeSession) kruntime).getId(),
                            null );
            	} else {
                    signalManager.signalEvent( "RuleFlowGroup_" + event.getRuleFlowGroup().getName(),
                            null );
            	}
            }
        } );
    }

	public void abortProcessInstance(long processInstanceId) {
		ProcessInstance processInstance = getProcessInstance(processInstanceId);
		if ( processInstance == null ) {
            throw new IllegalArgumentException( "Could not find process instance for id " + processInstanceId );
        }
        ((org.jbpm.process.instance.ProcessInstance) processInstance).setState( ProcessInstance.STATE_ABORTED );
	}

	public void suspendProcessInstance(long processInstanceId) {
		ProcessInstance processInstance = getProcessInstance(processInstanceId);
		if ( processInstance == null ) {
            throw new IllegalArgumentException( "Could not find process instance for id " + processInstanceId );
        }
        ((org.jbpm.process.instance.ProcessInstance) processInstance).setState( ProcessInstance.STATE_SUSPENDED );
	}

	public void resumeProcessInstance(long processInstanceId) {
		ProcessInstance processInstance = getProcessInstance(processInstanceId);
		if ( processInstance == null ) {
            throw new IllegalArgumentException( "Could not find process instance for id " + processInstanceId );
        }
        ((org.jbpm.process.instance.ProcessInstance) processInstance).setState( ProcessInstance.STATE_ACTIVE );
	}

	public WorkItemManager getWorkItemManager() {
		return kruntime.getWorkItemManager();
	}

	public void signalEvent(String type, Object event) {
		signalManager.signalEvent(type, event);
	}

	public void signalEvent(String type, Object event, long processInstanceId) {
		signalManager.signalEvent(processInstanceId, type, event);
	}
	
	public void setProcessEventSupport(ProcessEventSupport processEventSupport) {
		this.processEventSupport = processEventSupport;
	}
	
	public void dispose() {
        this.processEventSupport.reset();
        this.timerManager.dispose();
        if( kruntime != null ) { 
            kruntime.getKieBase().removeEventListener(knowledgeBaseListener);
            kruntime = null;
        }
        workingMemory = null;
	}

	public void clearProcessInstances() {
		this.processInstanceManager.clearProcessInstances();
	}

    public void clearProcessInstancesState() {
        this.processInstanceManager.clearProcessInstancesState();
        
    }


}
