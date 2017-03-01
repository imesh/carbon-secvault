/*
 * Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wso2.carbon.securevault;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.carbon.securevault.config.model.SecureVaultConfiguration;
import org.wso2.carbon.securevault.exception.SecureVaultException;
import org.wso2.carbon.securevault.internal.SecureVaultConfigurationProvider;
import org.wso2.carbon.securevault.internal.SecureVaultDataHolder;
import org.wso2.carbon.securevault.internal.SecureVaultImpl;
import org.wso2.carbon.securevault.reader.DefaultMasterKeyReader;
import org.wso2.carbon.securevault.repository.DefaultSecretRepository;

import java.util.Optional;
import java.util.ServiceLoader;

/**
 * Responsible for initializing secure vault.
 *
 * @since 5.2.0
 */
public class SecureVaultInitializer {

    public boolean initialized = false;

    private static final Logger logger = LoggerFactory.getLogger(SecureVaultInitializer.class);
    private static SecureVaultInitializer secureVaultInitializer = SecureVaultInitializer.getInstance();

    private Optional<SecureVaultConfiguration> optSecureVaultConfiguration;

    private String secretRepositoryType;
    private String masterKeyReaderType;

    private ServiceLoader<SecretRepository> secretRepositoryLoader;
    private ServiceLoader<MasterKeyReader> masterKeyReaderLoader;

    private Optional<String> secureVaultYAMLPath;
    private Optional<String> masterKeyYAMLPath;
    private Optional<String> secretPropertiesPath;

    private SecureVaultInitializer() {
    }

    /**
     * Initialise secret repository and master key reader types from values which is read from secure-vault.yaml.
     */
    public void initFromSecureVaultYAML() {
        try {
            optSecureVaultConfiguration = Optional.of(SecureVaultConfigurationProvider.getConfiguration());
            optSecureVaultConfiguration.ifPresent(secureVaultConfiguration -> {
                secretRepositoryType = secureVaultConfiguration.getSecretRepositoryConfig().getType().orElse("");
                masterKeyReaderType = secureVaultConfiguration.getMasterKeyReaderConfig().getType().orElse("");
            });
        } catch (SecureVaultException | RuntimeException e) {
            optSecureVaultConfiguration = Optional.empty();
            logger.error("Error while acquiring secure vault configuration", e);
        }
    }

    public static synchronized SecureVaultInitializer getInstance() {
        if (secureVaultInitializer == null) {
            return new SecureVaultInitializer();
        }
        return secureVaultInitializer;
    }

    /**
     * Initialise the secure vault for SPI case.
     *
     * @param masterKeysFilePath Master key file path which is used store keys and configs to unlock secret repository.
     * @param secretPropertiesFilePath Secret properties file path which is used to store keys and aliases.
     * @param secureVaultYAMLPath Secure vault yaml path which is used to store all configuration related to secure
     *                            vault.
     * @return SecureVault service instance.
     */
    public SecureVault initializeSecureVault(String masterKeysFilePath, String secretPropertiesFilePath,
                                      String secureVaultYAMLPath) {
        setMasterKeyYAMLPath(Optional.of(masterKeysFilePath));
        setSecretPropertiesPath(Optional.of(secretPropertiesFilePath));
        setSecureVaultYAMLPath(Optional.of(secureVaultYAMLPath));
        initFromSecureVaultYAML();
        secretRepositoryLoader = ServiceLoader.load(SecretRepository.class);
        masterKeyReaderLoader = ServiceLoader.load(MasterKeyReader.class);
        if (masterKeyReaderLoader.iterator().next() == null) {
            SecureVaultDataHolder.getInstance().setMasterKeyReader(new DefaultMasterKeyReader());
        } else {
            SecureVaultDataHolder.getInstance().setMasterKeyReader(masterKeyReaderLoader.iterator().next());
        }
        if (secretRepositoryLoader.iterator().next() == null) {
            SecureVaultDataHolder.getInstance().setSecretRepository(new DefaultSecretRepository());
        } else {
            SecureVaultDataHolder.getInstance().setSecretRepository(secretRepositoryLoader.iterator().next());
        }

        return initializeSecureVault();
    }

    /**
     * Initialize the secure vault by initialising master key reader and secret repository and loading secrets
     * to secret repository.
     */
    public SecureVault initializeSecureVault() {
        synchronized (this) {
            if (initialized) {
                logger.debug("Secure Vault Component is already initialized");
                return new SecureVaultImpl();
            }

            try {
                logger.debug("Initializing the secure vault with, SecretRepositoryType={}, MasterKeyReaderType={}",
                        secretRepositoryType, masterKeyReaderType);

                SecureVaultConfiguration secureVaultConfiguration = optSecureVaultConfiguration
                        .orElseThrow(() -> new SecureVaultException("Cannot initialize secure vault without " +
                                "secure vault configurations"));

                MasterKeyReader masterKeyReader = SecureVaultDataHolder.getInstance().getMasterKeyReader()
                        .orElseThrow(() ->
                                new SecureVaultException("Cannot initialise secure vault without master key reader"));
                SecretRepository secretRepository = SecureVaultDataHolder.getInstance().getSecretRepository()
                        .orElseThrow(() ->
                                new SecureVaultException("Cannot initialise secure vault without secret repository"));

                masterKeyReader.init(secureVaultConfiguration.getMasterKeyReaderConfig());
                secretRepository.init(secureVaultConfiguration.getSecretRepositoryConfig(), masterKeyReader);

                secretRepository.loadSecrets(secureVaultConfiguration.getSecretRepositoryConfig());

                initialized = true;
            } catch (SecureVaultException e) {
                logger.error("Failed to initialize Secure Vault.", e);
            }
        }

        logger.debug("Secure Vault initialized successfully");
        return new SecureVaultImpl();
    }

    public Optional<String> getSecureVaultYAMLPath() {
        return secureVaultYAMLPath;
    }

    public void setSecureVaultYAMLPath(Optional<String> secureVaultYAMLPath) {
        this.secureVaultYAMLPath = secureVaultYAMLPath;
    }

    public Optional<String> getMasterKeyYAMLPath() {
        return masterKeyYAMLPath;
    }

    public void setMasterKeyYAMLPath(Optional<String> masterKeyYAMLPath) {
        this.masterKeyYAMLPath = masterKeyYAMLPath;
    }

    public Optional<String> getSecretPropertiesPath() {
        return secretPropertiesPath;
    }

    public void setSecretPropertiesPath(Optional<String> secretPropertiesPath) {
        this.secretPropertiesPath = secretPropertiesPath;
    }

    public String getSecretRepositoryType() {
        return secretRepositoryType;
    }

    public String getMasterKeyReaderType() {
        return masterKeyReaderType;
    }
}
