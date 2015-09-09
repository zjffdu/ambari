/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.ambari.server.state.stack;

import org.apache.ambari.server.state.stack.upgrade.ConfigUpgradeChangeDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a pack of changes that should be applied to configs
 * when upgrading from a previous stack. In other words, it's a config delta
 * from prev stack
 */
@XmlRootElement(name="upgrade-config-changes")
@XmlAccessorType(XmlAccessType.FIELD)
public class ConfigUpgradePack {

  /**
   * Defines per-service config changes
   */
  @XmlElementWrapper(name="services")
  @XmlElement(name="service")
  private List<AffectedService> services;

  /**
   * Contains a cached mapping of <change id, change definition>.
   */
  private Map<String, ConfigUpgradeChangeDefinition> changesById;

  private static Logger LOG = LoggerFactory.getLogger(ConfigUpgradePack.class);

  /**
   * no-arg default constructor for JAXB
   */
  public ConfigUpgradePack() {
  }

  public ConfigUpgradePack(List<AffectedService> services) {
    this.services = services;
  }

  /**
   * @return a list of per-service config changes. List should not be modified
   * in runtime, since it will make cache stale.
   */
  public List<AffectedService> getServices() {
    return services;
  }

  /**
   * @return a map of <service name, AffectedService>.
   */
  public Map<String, AffectedService> getServiceMap() {
    Map<String, AffectedService> result = new HashMap<>();
    for (AffectedService service : services) {
      result.put(service.name, service);
    }
    return result;
  }

  /**
   * @return a map of <change id, change definition>. Map is built once and
   * cached
   */
  public Map<String, ConfigUpgradeChangeDefinition> enumerateConfigChangesByID() {
    if (changesById == null) {
      changesById = new HashMap<>();
      for(AffectedService service : services) {
        for(AffectedComponent component: service.components) {
          for (ConfigUpgradeChangeDefinition changeDefinition : component.changes) {
            if (changeDefinition.id == null) {
              LOG.warn(String.format("Config upgrade change definition for service %s," +
                      " component %s has no id", service.name, component.name));
            } else if (changesById.containsKey(changeDefinition.id)) {
              LOG.warn("Duplicate config upgrade change definition with ID " +
                      changeDefinition.id);
            }
            changesById.put(changeDefinition.id, changeDefinition);
          }
        }
      }
    }
    return changesById;
  }

  /**
   * A service definition in the 'services' element.
   */
  public static class AffectedService {

    @XmlAttribute
    public String name;

    @XmlElement(name="component")
    public List<AffectedComponent> components;

    /**
     * @return a map of <component name, AffectedService>
     */
    public Map<String, AffectedComponent> getComponentMap() {
      Map<String, AffectedComponent> result = new HashMap<>();
      for (AffectedComponent component : components) {
        result.put(component.name, component);
      }
      return result;
    }
  }

  /**
   * A component definition in the 'services/service' path.
   */
  public static class AffectedComponent {

    @XmlAttribute
    public String name;

    @XmlElementWrapper(name="changes")
    @XmlElement(name="definition")
    public List<ConfigUpgradeChangeDefinition> changes;

  }
}
