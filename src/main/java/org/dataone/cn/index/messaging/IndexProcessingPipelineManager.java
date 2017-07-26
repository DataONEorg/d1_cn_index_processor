package org.dataone.cn.index.messaging;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import javax.annotation.Resource;

import org.apache.log4j.Logger;
import org.dataone.cn.index.processor.IndexingMessageProcessor;
import org.dataone.cn.messaging.QueueAccess;
import org.dataone.configuration.Settings;
import org.springframework.amqp.core.MessageListener;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.listener.adapter.MessageListenerAdapter;
import org.springframework.context.ApplicationContext;

/**
 * A class that allocates consumers to queues, especially the number
 * of consumers (workers) for each.
 * @author rnahf
 *
 */
public class IndexProcessingPipelineManager {

    
    private Map<String,Integer> processingQueueAllocation = new HashMap<>();
    private Map<String,QueueAccess> processingQueueAccessRegistry = new HashMap<>();
    private static Logger logger = Logger.getLogger(IndexProcessingPipelineManager.class);
    
    
    public IndexProcessingPipelineManager(ApplicationContext context) {
        logger.info("IndexProcessingPipelineManager.IndexProcessingPipelineManager - the start point.");
        Properties props = Settings.getConfiguration().getProperties("dataone.index.queue");
        logger.info("IndexProcessingPipelineManager.IndexProcessingPipelineManager - the size of property \"dataone.index.queue\" is "+props.size());
        for (Object key: props.keySet()) {
        
            String queueName = (String) key;
            Integer count = new Integer((String)props.get(key));
            
            processingQueueAllocation.put(queueName, count);
            
            // effectively creates a naming convention for the beans
            // the listener has to have the same base name as the QueueAccess
            // and the base is the queueName
            String qaBean = queueName + "Access";
            String mlBean = queueName + "Listener";
            

            QueueAccess qa = (QueueAccess) context.getBean(qaBean);
            MessageListener ml = (MessageListener) context.getBean(mlBean);
            
            
            qa.registerAsynchronousMessageListener(count, ml);
            logger.info(String.format("Registered %d '%s' listeners to queue '%s'", count, mlBean, qaBean));
        }
    }
    
    public Map<String,Integer> getQueueAllocations()  {
        return processingQueueAllocation;
    }
    
    
    
}
