package org.dataone.cn.index.processor;

import org.apache.log4j.Logger;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

@DisallowConcurrentExecution
public class IndexTaskProcessorJob implements Job {

    private static Logger logger = Logger.getLogger(IndexTaskProcessorJob.class.getName());

    private static ApplicationContext context;
    private static IndexTaskProcessor processor;

    public IndexTaskProcessorJob() {
    }

    public void execute(JobExecutionContext arg0) throws JobExecutionException {
        logger.info("executing index task processor...");
        setContext();
        processor.processIndexTaskQueue();
    }

    private static void setContext() {
        if (context == null || processor == null) {
            context = new ClassPathXmlApplicationContext("processor-daemon-context.xml");
            processor = (IndexTaskProcessor) context.getBean("indexTaskProcessor");
        }
    }
}
