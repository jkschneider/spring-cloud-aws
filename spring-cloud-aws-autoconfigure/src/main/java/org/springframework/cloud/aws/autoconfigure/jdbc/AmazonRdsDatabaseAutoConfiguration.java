/*
 * Copyright 2013-2014 the original author or authors.
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

package org.springframework.cloud.aws.autoconfigure.jdbc;

import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.bind.PropertySourceUtils;
import org.springframework.cloud.aws.core.config.AmazonWebserviceClientConfigurationUtils;
import org.springframework.cloud.aws.jdbc.config.annotation.AmazonRdsInstanceConfiguration;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Agim Emruli
 * @author Alain Sahli
 */
@Configuration
@AutoConfigureBefore(DataSourceAutoConfiguration.class)
@Import(AmazonRdsDatabaseAutoConfiguration.Registrar.class)
@ConditionalOnClass(name = "com.amazonaws.services.rds.AmazonRDSClient")
@ConditionalOnMissingBean(AmazonRdsInstanceConfiguration.class)
public class AmazonRdsDatabaseAutoConfiguration {

	public static class Registrar extends AmazonRdsInstanceConfiguration.AbstractRegistrar implements EnvironmentAware {

		private static final String PREFIX = "cloud.aws.rds";

		private ConfigurableEnvironment environment;

		@Override
		public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
			String amazonRdsClientBeanName = AmazonWebserviceClientConfigurationUtils.
					registerAmazonWebserviceClient(this, registry, "com.amazonaws.services.rds.AmazonRDSClient", null, null).getBeanName();
			Map<String, Map<String, String>> dbInstanceConfigurations = getDbInstanceConfigurations();
			for (Map.Entry<String, Map<String, String>> dbInstanceEntry : dbInstanceConfigurations.entrySet()) {
				registerDataSource(registry, amazonRdsClientBeanName, dbInstanceEntry.getKey(), dbInstanceEntry.getValue().get("password"),
						Boolean.valueOf(dbInstanceEntry.getValue().containsKey("readReplicaSupport") ? dbInstanceEntry.getValue().get("readReplicaSupport") : "false"),
						dbInstanceEntry.getValue().get("username"), dbInstanceEntry.getValue().get("databaseName"));
			}
		}

		@Override
		public void setEnvironment(Environment environment) {
			Assert.isInstanceOf(ConfigurableEnvironment.class, environment, "Amazon RDS auto configuration requires a configurable environment");
			this.environment = (ConfigurableEnvironment) environment;
		}

		private Map<String, Map<String, String>> getDbInstanceConfigurations() {
			Map<String, Object> subProperties = PropertySourceUtils.getSubProperties(this.environment.getPropertySources(), PREFIX);
			Map<String, Map<String, String>> dbConfigurationMap = new HashMap<>(subProperties.keySet().size());
			for (Map.Entry<String, Object> subProperty : subProperties.entrySet()) {
				String instanceName = extractConfigurationSubPropertyGroup(subProperty.getKey());
				if (!dbConfigurationMap.containsKey(instanceName)) {
					dbConfigurationMap.put(instanceName, new HashMap<String, String>());
				}

				String subPropertyName = extractConfigurationSubPropertyName(subProperty.getKey());
				if (StringUtils.hasText(subPropertyName)) {
					dbConfigurationMap.get(instanceName).put(subPropertyName, (String) subProperty.getValue());
				}
			}
			return dbConfigurationMap;
		}

		private static String extractConfigurationSubPropertyGroup(String propertyName) {
			if (propertyName.lastIndexOf(".") > 1) {
				return propertyName.substring(1, propertyName.lastIndexOf("."));
			} else {
				return propertyName.substring(1);
			}

		}

		private static String extractConfigurationSubPropertyName(String propertyName) {
			if (!propertyName.contains(".")) {
				return propertyName;
			}
			return propertyName.substring(propertyName.lastIndexOf(".") + 1);
		}
	}
}