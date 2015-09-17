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

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.persist.PersistService;
import org.apache.ambari.server.api.services.AmbariMetaInfo;
import org.apache.ambari.server.orm.GuiceJpaInitializer;
import org.apache.ambari.server.orm.InMemoryDefaultTestModule;
import org.apache.ambari.server.state.stack.upgrade.ClusterGrouping;
import org.apache.ambari.server.state.stack.upgrade.ClusterGrouping.ExecuteStage;
import org.apache.ambari.server.state.stack.upgrade.ConfigUpgradeChangeDefinition;
import org.apache.ambari.server.state.stack.upgrade.Direction;
import org.apache.ambari.server.state.stack.upgrade.Grouping;
import org.apache.ambari.server.state.stack.upgrade.RestartGrouping;
import org.apache.ambari.server.state.stack.upgrade.StopGrouping;
import org.apache.ambari.server.state.stack.upgrade.UpgradeType;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import static org.apache.ambari.server.state.stack.ConfigUpgradePack.AffectedService;
import static org.apache.ambari.server.state.stack.ConfigUpgradePack.AffectedComponent;

/**
 * Tests for the config upgrade pack
 */
public class ConfigUpgradePackTest {

  @Test
  public void testMerge() {

    // Generate test data - 3 config upgrade packs, 2 services, 2 components, 2 config changes each
    ArrayList<ConfigUpgradePack> cups = new ArrayList<>();
    for (int cupIndex = 0; cupIndex < 3; cupIndex++) {

      ArrayList<AffectedService> services = new ArrayList<>();
      for (int serviceIndex = 0; serviceIndex < 2; serviceIndex++) {
        String serviceName;
        if (serviceIndex == 0) {
          serviceName = "HDFS";  // For checking merge of existing services
        } else {
          serviceName = String.format("SOME_SERVICE_%s", cupIndex);
        }
        ArrayList<AffectedComponent> components = new ArrayList<>();
        for (int componentIndex = 0; componentIndex < 2; componentIndex++) {
          String componentName;
          if (componentIndex == 0) {
            componentName = "NAMENODE";  // For checking merge of existing components
          } else {
            componentName = "SOME_COMPONENT_" + cupIndex;
          }

          ArrayList<ConfigUpgradeChangeDefinition> changeDefinitions = new ArrayList<>();
          for (int changeIndex = 0; changeIndex < 2; changeIndex++) {
            String change_id = String.format(
                    "CHANGE_%s_%s_%s_%s", cupIndex, serviceIndex, componentIndex, changeIndex);
            ConfigUpgradeChangeDefinition changeDefinition = new ConfigUpgradeChangeDefinition();
            changeDefinition.id = change_id;
            changeDefinitions.add(changeDefinition);
          }
          AffectedComponent component = new AffectedComponent();
          component.name = componentName;
          component.changes = changeDefinitions;
          components.add(component);
        }
        AffectedService service = new AffectedService();
        service.name = serviceName;
        service.components = components;
        services.add(service);
      }
      ConfigUpgradePack cupI = new ConfigUpgradePack();
      cupI.services = services;
      cups.add(cupI);
    }

    // Merge

    ConfigUpgradePack result = ConfigUpgradePack.merge(cups);


    // Check test results

    assertEquals(result.enumerateConfigChangesByID().entrySet().size(), 24);

    assertEquals(result.getServiceMap().get("HDFS").getComponentMap().get("NAMENODE").changes.get(0).id, "CHANGE_0_0_0_0");
    assertEquals(result.getServiceMap().get("HDFS").getComponentMap().get("NAMENODE").changes.get(1).id, "CHANGE_0_0_0_1");
    assertEquals(result.getServiceMap().get("HDFS").getComponentMap().get("NAMENODE").changes.get(2).id, "CHANGE_1_0_0_0");
    assertEquals(result.getServiceMap().get("HDFS").getComponentMap().get("NAMENODE").changes.get(3).id, "CHANGE_1_0_0_1");
    assertEquals(result.getServiceMap().get("HDFS").getComponentMap().get("NAMENODE").changes.get(4).id, "CHANGE_2_0_0_0");
    assertEquals(result.getServiceMap().get("HDFS").getComponentMap().get("NAMENODE").changes.get(5).id, "CHANGE_2_0_0_1");


    assertEquals(result.getServiceMap().get("HDFS").getComponentMap().get("SOME_COMPONENT_0").changes.get(0).id, "CHANGE_0_0_1_0");
    assertEquals(result.getServiceMap().get("HDFS").getComponentMap().get("SOME_COMPONENT_0").changes.get(1).id, "CHANGE_0_0_1_1");

    assertEquals(result.getServiceMap().get("HDFS").getComponentMap().get("SOME_COMPONENT_1").changes.get(0).id, "CHANGE_1_0_1_0");
    assertEquals(result.getServiceMap().get("HDFS").getComponentMap().get("SOME_COMPONENT_1").changes.get(1).id, "CHANGE_1_0_1_1");

    assertEquals(result.getServiceMap().get("HDFS").getComponentMap().get("SOME_COMPONENT_2").changes.get(0).id, "CHANGE_2_0_1_0");
    assertEquals(result.getServiceMap().get("HDFS").getComponentMap().get("SOME_COMPONENT_2").changes.get(1).id, "CHANGE_2_0_1_1");


    assertEquals(result.getServiceMap().get("SOME_SERVICE_0").getComponentMap().get("NAMENODE").changes.get(0).id, "CHANGE_0_1_0_0");
    assertEquals(result.getServiceMap().get("SOME_SERVICE_0").getComponentMap().get("NAMENODE").changes.get(1).id, "CHANGE_0_1_0_1");
    assertEquals(result.getServiceMap().get("SOME_SERVICE_0").getComponentMap().get("SOME_COMPONENT_0").changes.get(0).id, "CHANGE_0_1_1_0");
    assertEquals(result.getServiceMap().get("SOME_SERVICE_0").getComponentMap().get("SOME_COMPONENT_0").changes.get(1).id, "CHANGE_0_1_1_1");

    assertEquals(result.getServiceMap().get("SOME_SERVICE_1").getComponentMap().get("NAMENODE").changes.get(0).id, "CHANGE_1_1_0_0");
    assertEquals(result.getServiceMap().get("SOME_SERVICE_1").getComponentMap().get("NAMENODE").changes.get(1).id, "CHANGE_1_1_0_1");
    assertEquals(result.getServiceMap().get("SOME_SERVICE_1").getComponentMap().get("SOME_COMPONENT_1").changes.get(0).id, "CHANGE_1_1_1_0");
    assertEquals(result.getServiceMap().get("SOME_SERVICE_1").getComponentMap().get("SOME_COMPONENT_1").changes.get(1).id, "CHANGE_1_1_1_1");

    assertEquals(result.getServiceMap().get("SOME_SERVICE_2").getComponentMap().get("NAMENODE").changes.get(0).id, "CHANGE_2_1_0_0");
    assertEquals(result.getServiceMap().get("SOME_SERVICE_2").getComponentMap().get("NAMENODE").changes.get(1).id, "CHANGE_2_1_0_1");
    assertEquals(result.getServiceMap().get("SOME_SERVICE_2").getComponentMap().get("SOME_COMPONENT_2").changes.get(0).id, "CHANGE_2_1_1_0");
    assertEquals(result.getServiceMap().get("SOME_SERVICE_2").getComponentMap().get("SOME_COMPONENT_2").changes.get(1).id, "CHANGE_2_1_1_1");

  }

}
