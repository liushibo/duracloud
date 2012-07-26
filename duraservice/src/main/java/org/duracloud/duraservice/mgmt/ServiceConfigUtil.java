/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.duraservice.mgmt;

import org.duracloud.client.ContentStore;
import org.duracloud.client.ContentStoreManager;
import org.duracloud.common.constant.Constants;
import org.duracloud.common.model.Credential;
import org.duracloud.duraservice.domain.ServiceComputeInstance;
import org.duracloud.duraservice.domain.Store;
import org.duracloud.duraservice.domain.UserStore;
import org.duracloud.duraservice.error.ServiceException;
import org.duracloud.error.ContentStoreException;
import org.duracloud.serviceconfig.Deployment;
import org.duracloud.serviceconfig.DeploymentOption;
import org.duracloud.serviceconfig.ServiceInfo;
import org.duracloud.serviceconfig.SystemConfig;
import org.duracloud.serviceconfig.user.MultiSelectUserConfig;
import org.duracloud.serviceconfig.user.Option;
import org.duracloud.serviceconfig.user.SelectableUserConfig;
import org.duracloud.serviceconfig.user.SingleSelectUserConfig;
import org.duracloud.serviceconfig.user.UserConfig;
import org.duracloud.serviceconfig.user.UserConfigMode;
import org.duracloud.serviceconfig.user.UserConfigModeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Performs utility functions for service configuration.
 *
 * @author Bill Branan
 */
public class ServiceConfigUtil {

    private static final Logger log = LoggerFactory.getLogger(ServiceConfigUtil.class);

    // User Config Variables
    /* Expands to include all stores as Options for a Selectable Config */
    public static final String STORES_VAR = "$STORES";
    /* Expands to include all spaces within a store as Options for a Selectable Config*/
    public static final String SPACES_VAR = "$SPACES";
    /* Expands to create a UserConfig for each space within a store */
    public static final String SPACES_CONFIG_VAR = "$SPACES_CONFIG";
    /* Expands to create a Mode (within a ModeSet) for each store */
    public static final String ALL_STORE_SPACES_VAR = "$ALL_STORE_SPACES";

    /* Used as a prefix for space IDs when they are included as user config names */
    public static final String SPACE_PREFIX = "spaceID-";

    // System Config Variables
    public static final String STORE_HOST_VAR = "$DURASTORE-HOST";
    public static final String STORE_PORT_VAR = "$DURASTORE-PORT";
    public static final String STORE_CONTEXT_VAR = "$DURASTORE-CONTEXT";
    public static final String STORE_MSG_BROKER_VAR = "$MESSAGE-BROKER-URL";
    public static final String STORE_USER_VAR = "$DURASTORE-USERNAME";
    public static final String STORE_PWORD_VAR = "$DURASTORE-PASSWORD";
    public static final String SVC_LAUNCHING_USER_VAR = "$SVC-LAUNCHING-USER";

    // Provides access to user storage
    private final ContentStoreManagerUtil contentStoreManagerUtil;

    // Temporary cached elements
    private ContentStoreManager contentStoreManager;

    /**
     * Creates a service config utility with access to a user's content store
     *
     * @throws ContentStoreException if the content store is not available
     */
    public ServiceConfigUtil(ContentStoreManagerUtil contentStoreManagerUtil)
        throws ContentStoreException {
        this.contentStoreManagerUtil = contentStoreManagerUtil;
    }

    /*
     * Handles the population of all deployment and configuration options
     * for a service which is available for deployment.
     *
     * @return a clone of the provided service with all variables resolved
     */
    public ServiceInfo populateServiceKeepCache(ServiceInfo service,
                                                List<ServiceComputeInstance> serviceComputeInstances,
                                                UserStore userStore,
                                                String primaryHostName) {
        log.debug("populateServiceKeepCache: " + service.getContentId());
        return doPopulateService(service,
                                 serviceComputeInstances,
                                 userStore,
                                 primaryHostName);
    }

    public ServiceInfo populateService(ServiceInfo service,
                                       List<ServiceComputeInstance> serviceComputeInstances,
                                       UserStore userStore,
                                       String primaryHostName) {
        log.debug("populateService: " + service.getContentId());
        clearCache();
        return doPopulateService(service,
                                 serviceComputeInstances,
                                 userStore,
                                 primaryHostName);
    }

    private ServiceInfo doPopulateService(ServiceInfo service,
                                         List<ServiceComputeInstance> serviceComputeInstances,
                                         UserStore userStore,
                                         String primaryHostName) {
        log.debug("populateService: " + service.getContentId());
        // Perform a deep clone of the service (includes all configs and deployments)
        ServiceInfo srvClone = getClone(service);

        // Populate deployment options
        List<DeploymentOption> populatedDeploymentOptions = populateDeploymentOptions(
            srvClone.getDeploymentOptions(),
            serviceComputeInstances,
            primaryHostName);
        srvClone.setDeploymentOptions(populatedDeploymentOptions);

        ContentStoreManager userStoreManager = getUserStoreManager(userStore);

        // Populate variables in mode sets ($ALL_STORE_SPACES)
        List<UserConfigModeSet> populatedModeSets = populateModeSetVariables(
            userStoreManager,
            srvClone.getUserConfigModeSets());
        srvClone.setUserConfigModeSets(populatedModeSets);

        // Remove system configs
        srvClone.setSystemConfigs(null);

        // Remove system configs from deployments
        List<Deployment> deployments = srvClone.getDeployments();
        if(deployments != null) {
            for(Deployment deployment : deployments) {
                deployment.setSystemConfigs(null);
            }
        }

        return srvClone;
    }

    private ServiceInfo getClone(ServiceInfo service) {
        try {
            return service.clone();

        } catch (CloneNotSupportedException e) {
            String msg = "Error cloning: " + service + ", " + e.getMessage();
            log.error(msg);
            throw new ServiceException(msg, e);
        }
    }

    private ContentStoreManager getUserStoreManager(Store store) {
        if (null == this.contentStoreManager) {
            this.contentStoreManager = contentStoreManagerUtil.getContentStoreManager(
                store);
        }
        return this.contentStoreManager;
    }

    public void clearCache() {
        this.contentStoreManager = null;
    }

    /**
     * Handles the population of Option sets for variables $STORES and $SPACES
     * as well as populating the UserConfigs for variable $SPACES_CONFIG.
     *
     * @param userConfigs user configuration for a service
     * @return the populated user configuration list
     */
    protected List<UserConfig> populateUserConfigVariables(ContentStore contentStore,
                                                           ContentStoreManager userStoreManager,
                                                           List<UserConfig> userConfigs) {
        List<UserConfig> newUserConfigs1 = new ArrayList<UserConfig>();
        List<UserConfig> newUserConfigs2 = new ArrayList<UserConfig>();
        if(userConfigs != null){
            Map<String, ContentStore> storesMap = getContentStores(userStoreManager);
            String storeId = contentStore.getStoreId();

            // Expand the SPACES_CONFIG variable
            for(UserConfig config : userConfigs) {
                String configName = config.getName();
                if(null != configName && configName.equals(SPACES_CONFIG_VAR)) {
                    try {
                        List<String> spaces = contentStore.getSpaces();
                        for(String spaceId : spaces) {
                            // Create a config for the (non-system) space
                            if(!Constants.SYSTEM_SPACES.contains(spaceId)) {
                                UserConfig newUserConfig = config.clone();
                                newUserConfig.setName(SPACE_PREFIX + spaceId);
                                newUserConfig.setDisplayName(spaceId);
                                // Add the new config to the list
                                newUserConfigs1.add(newUserConfig);
                            }
                        }
                    } catch(CloneNotSupportedException cnse) {
                        String error =
                            "Error encountered attempting to clone user " +
                            "configuration in order to expand the variable " +
                            SPACES_CONFIG_VAR + ": " + cnse.getMessage();
                        throw new RuntimeException(error, cnse);
                    } catch(ContentStoreException cse) {
                        String error =
                            "Error encountered attempting to get spaces for " +
                            "store with ID " + contentStore.getStoreId() +
                            ": " + cse.getMessage();
                        throw new RuntimeException(error, cse);
                    }
                } else {
                    newUserConfigs1.add(config);
                }
            }

            // Expand the STORES and SPACES variables
            for(UserConfig config : newUserConfigs1) {
                if(config instanceof SelectableUserConfig) {
                    List<Option> options = ((SelectableUserConfig) config).getOptions();
                    options = populateStoresVariable(storeId, storesMap, options);
                    options = populateSpacesVariable(contentStore, options);
                    if (config instanceof SingleSelectUserConfig) {
                        SingleSelectUserConfig newConfig = new SingleSelectUserConfig(
                            config.getName(),
                            config.getDisplayName(),
                            options,
                            config.getExclusion());
                        newUserConfigs2.add(newConfig);
                    } else if (config instanceof MultiSelectUserConfig) {
                        MultiSelectUserConfig newConfig = new MultiSelectUserConfig(
                            config.getName(),
                            config.getDisplayName(),
                            options,
                            config.getExclusion());
                        newUserConfigs2.add(newConfig);
                    } else {
                        throw new RuntimeException("Unexpected UserConfig type: " +
                                                   config.getClass());
                    }
                } else {
                    newUserConfigs2.add(config);
                }
            }
        }
        return newUserConfigs2;
    }

    private ContentStore getPrimaryContentStore(ContentStoreManager userStoreManager) {
        try {
            return userStoreManager.getPrimaryContentStore();

        } catch (ContentStoreException e) {
            String msg = "Error retrieving primaryContentStore.";
            log.error(msg, e);
            throw new RuntimeException(msg, e);
        }
    }

    private Map<String, ContentStore> getContentStores(ContentStoreManager userStoreManager) {
        try {
            return userStoreManager.getContentStores();

        } catch (ContentStoreException e) {
            String msg = "Error retrieving contentStores.";
            log.error(msg, e);
            throw new RuntimeException(msg, e);
        }
    }

    /*
     * Populates the $STORES variable
     */
    private List<Option> populateStoresVariable(String contentStoreId,
                                                Map<String, ContentStore> contentStores,
                                                List<Option> options) {
        List<Option> newOptionsList = new ArrayList<Option>();
        for (Option option : options) {
            String value = option.getValue();
            if (value.equals(STORES_VAR)) {
                for (String storeId : contentStores.keySet()) {
                    // Only provide options that are not the current storeId
                    if(!storeId.equals(contentStoreId)) {
                        ContentStore contentStore = contentStores.get(storeId);
                        String displayName =
                            contentStore.getStorageProviderType();
                        Option storeOption =
                            new Option(displayName, storeId, false);
                        newOptionsList.add(storeOption);
                    }
                }
            } else {
                newOptionsList.add(option);
            }
        }
        return newOptionsList;
    }

    /*
     * Populates the $SPACES variable
     */
    private List<Option> populateSpacesVariable(ContentStore contentStore,
                                                List<Option> options) {
        List<Option> newOptionsList = new ArrayList<Option>();
        for (Option option : options) {
            String value = option.getValue();
            if (value.equals(SPACES_VAR)) {
                try {
                    List<String> spaces = contentStore.getSpaces();
                    for(String spaceId : spaces) {
                        Option storeOption = new Option(spaceId, spaceId, false);
                        newOptionsList.add(storeOption);
                    }
                } catch(ContentStoreException cse) {
                    String error = "Error encountered attempting to construct user" +
                                   " content spaces options " + cse.getMessage();
                    throw new RuntimeException(error, cse);
                }
            } else {
                newOptionsList.add(option);
            }
        }
        return newOptionsList;
    }

    private List<UserConfigModeSet> populateModeSetVariables(ContentStoreManager userStoreManager,
                                                             List<UserConfigModeSet> modeSets) {
        List<UserConfigModeSet> newModeSets = new ArrayList<UserConfigModeSet>();
        if (null == modeSets) {
            return newModeSets;
        }

        for (UserConfigModeSet modeSet : modeSets) {
            List<UserConfigMode> modes = modeSet.getModes();
            List<UserConfigMode> newModes = null;

            UserConfigModeSet newModeSet = new UserConfigModeSet();
            newModeSet.setName(modeSet.getName());
            newModeSet.setDisplayName(modeSet.getDisplayName());

            String value = modeSet.getValue();
            if (null != value && value.equals(ALL_STORE_SPACES_VAR)) {
                newModes = createModeForEachStore(userStoreManager, modes);

            } else {
                newModes = populateModeVariables(userStoreManager, modes);
            }

            newModeSet.setModes(newModes);
            newModeSets.add(newModeSet);
        }

        return newModeSets;
    }

    private List<UserConfigMode> createModeForEachStore(ContentStoreManager userStoreManager,
                                                        List<UserConfigMode> modes) {
        List<UserConfigMode> newModes = new ArrayList<UserConfigMode>();

        Map<String, ContentStore> contentStores = getContentStores(
            userStoreManager);
        for (String storeId : contentStores.keySet()) {
            ContentStore contentStore = contentStores.get(storeId);
            newModes.add(createModeForStore(contentStore,
                                            userStoreManager,
                                            modes));
        }

        return newModes;
    }

    private UserConfigMode createModeForStore(ContentStore contentStore,
                                              ContentStoreManager userStoreManager,
                                              List<UserConfigMode> modes) {
        String storeName = contentStore.getStorageProviderType();
        String storeId = contentStore.getStoreId();
        String primaryStoreId = getPrimaryContentStore(userStoreManager).getStoreId();

        UserConfigMode newMode = new UserConfigMode();
        newMode.setName(storeId);
        newMode.setDisplayName(storeName);
        newMode.setSelected(primaryStoreId.equals(storeId));

        if (null != modes) {
            for (UserConfigMode mode : modes) {
                List<UserConfig> userConfigs = populateUserConfigVariables(
                    contentStore,
                    userStoreManager,
                    mode.getUserConfigs());

                newMode.setUserConfigs(userConfigs);
            }
        }
        
        return newMode;
    }

    private List<UserConfigMode> populateModeVariables(ContentStoreManager userStoreManager,
                                                       List<UserConfigMode> modes) {
        List<UserConfigMode> newModes = new ArrayList<UserConfigMode>();
        if (null == modes) {
            return newModes;
        }

        ContentStore primaryStore = getPrimaryContentStore(userStoreManager);
        for (UserConfigMode mode : modes) {
            mode.setUserConfigs(populateUserConfigVariables(primaryStore,
                                                            userStoreManager,
                                                            mode.getUserConfigs()));
            mode.setUserConfigModeSets(populateModeSetVariables(userStoreManager,
                                                                mode.getUserConfigModeSets()));

            newModes.add(mode);
        }

        return newModes;
    }

    private List<String> getSpaces(ContentStore contentStore) {
        try {
            return contentStore.getSpaces();

        } catch (ContentStoreException e) {
            String msg = "Error retrieving spaces for contentStore: " +
                contentStore.getStorageProviderType();
            log.error(msg, e);
            throw new RuntimeException(msg, e);
        }
    }

    /*
     * Populates the list of existing service instances. Ensures that the
     * EXISTING type is available, then adds all of the currently available
     * service instances to the list as options for deploying this service.
     */
    private List<DeploymentOption> populateDeploymentOptions(List<DeploymentOption> deploymentOptions,
                                                             List<ServiceComputeInstance> serviceComputeInstances,
                                                             String primaryHostName) {
        List<DeploymentOption> newDeploymentOptions =
            new ArrayList<DeploymentOption>();
        for(DeploymentOption depOp : deploymentOptions) {
            if(depOp.getState().equals(DeploymentOption.State.AVAILABLE)) {
                if(depOp.getLocation().equals(DeploymentOption.Location.EXISTING)) {
                    for(ServiceComputeInstance computeInstance : serviceComputeInstances) {
                        String hostName = computeInstance.getHostName();
                        if(!hostName.equals(primaryHostName) &&
                           !computeInstance.isLocked()) {
                            DeploymentOption newDepOpt = new DeploymentOption();
                            newDepOpt.setHostname(hostName);
                            newDepOpt.setDisplayName(computeInstance.getDisplayName());
                            newDepOpt.setLocation(DeploymentOption.Location.EXISTING);
                            newDepOpt.setState(DeploymentOption.State.AVAILABLE);
                            newDeploymentOptions.add(newDepOpt);
                        }
                    }
                } else if(depOp.getLocation().equals(DeploymentOption.Location.NEW)) {
                    DeploymentOption newDepOpt = new DeploymentOption();
                    newDepOpt.setHostname(ServiceManager.NEW_SERVICE_HOST);
                    newDepOpt.setDisplayName(ServiceManager.NEW_HOST_DISPLAY);
                    newDepOpt.setLocation(DeploymentOption.Location.NEW);
                    newDepOpt.setState(DeploymentOption.State.AVAILABLE);
                    newDeploymentOptions.add(newDepOpt);
                } else if(depOp.getLocation().equals(DeploymentOption.Location.PRIMARY)) {
                    DeploymentOption newDepOpt = new DeploymentOption();
                    newDepOpt.setHostname(primaryHostName);
                    newDepOpt.setDisplayName(getPrimaryHostDisplay(serviceComputeInstances,
                                                                   primaryHostName));
                    newDepOpt.setLocation(DeploymentOption.Location.PRIMARY);
                    newDepOpt.setState(DeploymentOption.State.AVAILABLE);
                    newDeploymentOptions.add(newDepOpt);
                } else {
                    newDeploymentOptions.add(depOp);
                }
            }
        }
        return newDeploymentOptions;
    }

    /*
     * Gets the display name of the primary host
     */
    private String getPrimaryHostDisplay(List<ServiceComputeInstance> serviceComputeInstances,
                                         String primaryHostName) {
        for(ServiceComputeInstance computeInstance : serviceComputeInstances) {
            String hostName = computeInstance.getHostName();
            if(hostName.equals(primaryHostName)) {
                return computeInstance.getDisplayName();
            }
        }
        return ServiceManager.PRIMARY_HOST_DISPLAY;
    }

    /**
     * Populates variables in a service's system configuration.
     *
     * @param systemConfig the system configuration of a service
     * @return the populated system configuration
     */
    public List<SystemConfig> resolveSystemConfigVars(UserStore userStore,
                                                      List<SystemConfig> systemConfig) {
        List<SystemConfig> newConfigList = new ArrayList<SystemConfig>();
        Credential user = contentStoreManagerUtil.getCurrentUser();
        for (SystemConfig config : systemConfig) {
            String configValue = config.getValue();
            SystemConfig newConfig = new SystemConfig(config.getName(),
                                                      configValue,
                                                      config.getDefaultValue());
            if(configValue.equals(STORE_HOST_VAR)) {
                newConfig.setValue(userStore.getHost());
            } else if(configValue.equals(STORE_PORT_VAR)) {
                newConfig.setValue(userStore.getPort());
            } else if(configValue.equals(STORE_CONTEXT_VAR)) {
                newConfig.setValue(userStore.getContext());
            } else if(configValue.equals(STORE_MSG_BROKER_VAR)) {
                newConfig.setValue(userStore.getMsgBrokerUrl());
            } else if (configValue.equals(STORE_USER_VAR)) {
                newConfig.setValue(user.getUsername());
            } else if (configValue.equals(STORE_PWORD_VAR)) {
                newConfig.setValue(user.getPassword());
            } else if (configValue.equals(SVC_LAUNCHING_USER_VAR)) {
                newConfig.setValue(user.getUsername());
            }
            newConfigList.add(newConfig);
        }
        return newConfigList;
    }

}