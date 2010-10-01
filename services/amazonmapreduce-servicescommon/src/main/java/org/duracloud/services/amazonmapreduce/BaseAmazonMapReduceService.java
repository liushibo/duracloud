/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.services.amazonmapreduce;

import org.duracloud.client.ContentStore;
import org.duracloud.client.ContentStoreManager;
import org.duracloud.client.ContentStoreManagerImpl;
import org.duracloud.common.error.DuraCloudRuntimeException;
import org.duracloud.common.model.Credential;
import org.duracloud.error.ContentStoreException;
import org.duracloud.services.BaseService;
import org.duracloud.services.ComputeService;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Dictionary;
import java.util.HashMap;
import java.util.Map;

import static org.duracloud.services.amazonmapreduce.AmazonMapReduceJobWorker.JobStatus;
import static org.duracloud.storage.domain.HadoopTypes.INSTANCES;
import static org.duracloud.storage.domain.HadoopTypes.INSTANCES.SMALL;
import static org.duracloud.storage.domain.HadoopTypes.STOP_JOB_TASK_NAME;
import static org.duracloud.storage.domain.HadoopTypes.TASK_PARAMS;

/**
 * This service contains the base logic common across services leveraging the
 * Amazon elastic map reduce framework.
 * It primarily collects configuration properties and starts task workers.
 *
 * @author Andrew Woods
 *         Date: Sept 29, 2010
 */
public abstract class BaseAmazonMapReduceService extends BaseService implements ComputeService, ManagedService {

    private final Logger log = LoggerFactory.getLogger(
        BaseAmazonMapReduceService.class);

    private static final String DEFAULT_DURASTORE_HOST = "localhost";
    private static final String DEFAULT_DURASTORE_PORT = "8080";
    private static final String DEFAULT_DURASTORE_CONTEXT = "durastore";
    private static final String DEFAULT_SOURCE_SPACE_ID = "service-source";
    private static final String DEFAULT_DEST_SPACE_ID = "service-dest";
    private static final String DEFAULT_WORK_SPACE_ID = "service-work";
    private static final String DEFAULT_NUM_INSTANCES = "1";
    private static final String DEFAULT_INSTANCE_TYPE = SMALL.getId();
    private static final String DEFAULT_NUM_MAPPERS = "1";

    private String duraStoreHost;
    private String duraStorePort;
    private String duraStoreContext;
    private String username;
    private String password;
    private String sourceSpaceId;
    private String destSpaceId;
    private String workSpaceId;
    private String numInstances;
    private String instanceType;
    private String mappersPerInstance;

    private ContentStore contentStore;

    protected abstract AmazonMapReduceJobWorker getJobWorker();

    protected abstract AmazonMapReduceJobWorker getPostJobWorker();

    protected abstract String getJobType();

    @Override
    public void start() throws Exception {
        log.info("Starting " + getServiceId() + " as " + username);
        this.setServiceStatus(ServiceStatus.STARTING);

        startWorker(getJobWorker());
        startWorker(getPostJobWorker());

        this.setServiceStatus(ServiceStatus.STARTED);
    }

    private void startWorker(Runnable worker) {
        if (worker != null) {
            new Thread(worker).start();
            log.info("started worker of class: " + worker.getClass());
        }
    }

    protected Map<String, String> collectTaskParams() {
        Map<String, String> taskParams = new HashMap<String, String>();

        taskParams.put(TASK_PARAMS.JOB_TYPE.name(), getJobType());
        taskParams.put(TASK_PARAMS.WORKSPACE_ID.name(), workSpaceId);
        taskParams.put(TASK_PARAMS.SOURCE_SPACE_ID.name(), sourceSpaceId);
        taskParams.put(TASK_PARAMS.DEST_SPACE_ID.name(), destSpaceId);
        taskParams.put(TASK_PARAMS.INSTANCE_TYPE.name(), instanceType);
        taskParams.put(TASK_PARAMS.NUM_INSTANCES.name(), numInstances);

        // Set max mappers based on instance type if the value is default
        String mappers = mappersPerInstance;
        if (DEFAULT_NUM_MAPPERS.equals(mappers)) {
            if (INSTANCES.LARGE.getId().equals(instanceType)) {
                mappers = "2";
            } else if (INSTANCES.XLARGE.getId().equals(instanceType)) {
                mappers = "4";
            }
        }
        taskParams.put(TASK_PARAMS.MAPPERS_PER_INSTANCE.name(), mappers);

        return taskParams;
    }

    @Override
    public void stop() throws Exception {
        log.info("Stopping " + getServiceId());
        this.setServiceStatus(ServiceStatus.STOPPING);

        String jobId = null;
        if (getJobWorker() != null) {
            getJobWorker().shutdown();

            jobId = getJobWorker().getJobId();
        }

        // Stop hadoop job
        if (jobId != null) {
            getContentStore().performTask(STOP_JOB_TASK_NAME, jobId);
        }

        if (getPostJobWorker() != null) {
            getPostJobWorker().shutdown();
        }

        this.setServiceStatus(ServiceStatus.STOPPED);
    }

    @Override
    public Map<String, String> getServiceProps() {
        Map<String, String> props = super.getServiceProps();

        AmazonMapReduceJobWorker.JobStatus jobStatus = JobStatus.UNKNOWN;
        AmazonMapReduceJobWorker worker = getJobWorker();
        if (worker != null) {
            jobStatus = worker.getJobStatus();

            String error = worker.getError();
            if (error != null) {
                props.put("Errors Encountered", error);
            }

            String jobId = worker.getJobId();
            if (jobId != null) {
                props.put("Job ID", jobId);
            }

            Map<String, String> jobDetailsMap = worker.getJobDetailsMap();
            for (String key : jobDetailsMap.keySet()) {
                props.put(key, jobDetailsMap.get(key));
            }
        }

        if (jobStatus.isComplete()) {
            AmazonMapReduceJobWorker postWorker = getPostJobWorker();
            if (postWorker != null) {
                jobStatus = postWorker.getJobStatus();
            }
        }

        props.put("Service Status", jobStatus.getDescription());
        log.info("Service Status: " + jobStatus.getDescription());

        return props;
    }

    public void updated(Dictionary config) throws ConfigurationException {
        log("Attempt made to update " + getJobType() +
            " service configuration via updated method. " +
            "Updates should occur via class setters.");
    }

    public String getDuraStoreHost() {
        return duraStoreHost;
    }

    public void setDuraStoreHost(String duraStoreHost) {
        if (duraStoreHost != null && !duraStoreHost.equals("")) {
            this.duraStoreHost = duraStoreHost;
        } else {
            log("Attempt made to set duraStoreHost to " + duraStoreHost +
                ", which is not valid. Setting value to default: " +
                DEFAULT_DURASTORE_HOST);
            this.duraStoreHost = DEFAULT_DURASTORE_HOST;
        }
    }

    public String getDuraStorePort() {
        return duraStorePort;
    }

    public void setDuraStorePort(String duraStorePort) {
        if (duraStorePort != null) {
            this.duraStorePort = duraStorePort;
        } else {
            log("Attempt made to set duraStorePort to null, which is not " +
                "valid. Setting value to default: " + DEFAULT_DURASTORE_PORT);
            this.duraStorePort = DEFAULT_DURASTORE_PORT;
        }
    }

    public String getDuraStoreContext() {
        return duraStoreContext;
    }

    public void setDuraStoreContext(String duraStoreContext) {
        if (duraStoreContext != null && !duraStoreContext.equals("")) {
            this.duraStoreContext = duraStoreContext;
        } else {
            log("Attempt made to set duraStoreContext to null or empty, " +
                "which is not valid. Setting value to default: " +
                DEFAULT_DURASTORE_CONTEXT);
            this.duraStoreContext = DEFAULT_DURASTORE_CONTEXT;
        }
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getSourceSpaceId() {
        return sourceSpaceId;
    }

    public void setSourceSpaceId(String sourceSpaceId) {
        if (sourceSpaceId != null && !sourceSpaceId.equals("")) {
            this.sourceSpaceId = sourceSpaceId;
        } else {
            log("Attempt made to set sourceSpaceId to null or empty, " +
                ", which is not valid. Setting value to default: " +
                DEFAULT_SOURCE_SPACE_ID);
            this.sourceSpaceId = DEFAULT_SOURCE_SPACE_ID;
        }
    }

    public String getDestSpaceId() {
        return destSpaceId;
    }

    public void setDestSpaceId(String destSpaceId) {
        if (destSpaceId != null && !destSpaceId.equals("")) {
            this.destSpaceId = destSpaceId;
        } else {
            log("Attempt made to set destSpaceId to to null or empty, " +
                ", which is not valid. Setting value to default: " +
                DEFAULT_DEST_SPACE_ID);
            this.destSpaceId = DEFAULT_DEST_SPACE_ID;
        }
    }

    public String getWorkSpaceId() {
        return workSpaceId;
    }

    public void setWorkSpaceId(String workSpaceId) {
        if (workSpaceId != null && !workSpaceId.equals("")) {
            this.workSpaceId = workSpaceId;
        } else {
            log("Attempt made to set workSpaceId to null or empty, " +
                ", which is not valid. Setting value to default: " +
                DEFAULT_WORK_SPACE_ID);
            this.workSpaceId = DEFAULT_WORK_SPACE_ID;
        }
    }

    public String getNumInstances() {
        return numInstances;
    }

    public void setNumInstances(String numInstances) {
        if (numInstances != null && !numInstances.equals("")) {
            try {
                Integer.valueOf(numInstances);
                this.numInstances = numInstances;
            } catch (NumberFormatException e) {
                log("Attempt made to set numInstances to a non-numerical " +
                    "value, which is not valid. Setting value to default: " +
                    DEFAULT_NUM_INSTANCES);
                this.numInstances = DEFAULT_NUM_INSTANCES;
            }
        } else {
            log("Attempt made to set numInstances to to null or empty, " +
                ", which is not valid. Setting value to default: " +
                DEFAULT_NUM_INSTANCES);
            this.numInstances = DEFAULT_NUM_INSTANCES;
        }
    }

    public String getInstanceType() {
        return instanceType;
    }

    public void setInstanceType(String instanceType) {

        if (instanceType != null && !instanceType.equals("")) {
            this.instanceType = instanceType;
        } else {
            log("Attempt made to set typeOfInstance to null or empty, " +
                ", which is not valid. Setting value to default: " +
                DEFAULT_INSTANCE_TYPE);
            this.instanceType = DEFAULT_INSTANCE_TYPE;
        }
    }

    public String getMappersPerInstance() {
        return mappersPerInstance;
    }

    public void setMappersPerInstance(String mappersPerInstance) {
        this.mappersPerInstance = mappersPerInstance;

        if (mappersPerInstance != null && !mappersPerInstance.equals("")) {
            try {
                Integer.valueOf(mappersPerInstance);
                this.mappersPerInstance = mappersPerInstance;
            } catch (NumberFormatException e) {
                log("Attempt made to set mappersPerInstance to a " +
                    "non-numerical value, which is not valid. Setting " +
                    "value to default: " + DEFAULT_NUM_MAPPERS);
                this.mappersPerInstance = DEFAULT_NUM_MAPPERS;
            }
        } else {
            log("Attempt made to set mappersPerInstance to to null or empty, " +
                ", which is not valid. Setting value to default: " +
                DEFAULT_NUM_MAPPERS);
            this.mappersPerInstance = DEFAULT_NUM_MAPPERS;
        }
    }

    public ContentStore getContentStore() {
        if (null == contentStore) {
            ContentStoreManager storeManager = new ContentStoreManagerImpl(
                duraStoreHost,
                duraStorePort,
                duraStoreContext);
            storeManager.login(new Credential(username, password));

            try {
                contentStore = storeManager.getPrimaryContentStore();

            } catch (ContentStoreException e) {
                log.error(e.getMessage());
                throw new DuraCloudRuntimeException(e);
            }
        }
        return contentStore;
    }

    public void setContentStore(ContentStore contentStore) {
        this.contentStore = contentStore;
    }

    private void log(String logMsg) {
        log.warn(logMsg);
    }
}