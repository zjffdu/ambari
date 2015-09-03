/*
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
package org.apache.ambari.server.upgrade;


import static org.easymock.EasyMock.anyLong;
import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.createNiceMock;
import static org.easymock.EasyMock.createStrictMock;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.reset;
import static org.easymock.EasyMock.verify;

import com.google.inject.Provider;
import com.google.inject.persist.PersistService;
import org.apache.ambari.server.AmbariException;
import org.apache.ambari.server.api.services.AmbariMetaInfo;
import org.apache.ambari.server.configuration.Configuration;
import org.apache.ambari.server.controller.AmbariManagementController;
import org.apache.ambari.server.orm.DBAccessor;
import org.apache.ambari.server.orm.GuiceJpaInitializer;
import org.apache.ambari.server.orm.InMemoryDefaultTestModule;
import org.apache.ambari.server.orm.dao.ClusterDAO;
import org.apache.ambari.server.orm.dao.ClusterVersionDAO;
import org.apache.ambari.server.orm.dao.HostVersionDAO;
import org.apache.ambari.server.orm.dao.RepositoryVersionDAO;
import org.apache.ambari.server.orm.dao.StackDAO;
import org.apache.ambari.server.orm.entities.ClusterEntity;
import org.apache.ambari.server.orm.entities.ClusterVersionEntity;
import org.apache.ambari.server.orm.entities.HostEntity;
import org.apache.ambari.server.orm.entities.HostVersionEntity;
import org.apache.ambari.server.orm.entities.RepositoryVersionEntity;
import org.apache.ambari.server.orm.entities.StackEntity;
import org.apache.ambari.server.stack.StackManagerFactory;
import org.apache.ambari.server.state.Cluster;
import org.apache.ambari.server.state.Clusters;
import org.apache.ambari.server.state.RepositoryVersionState;
import org.apache.ambari.server.state.StackId;
import org.apache.ambari.server.state.StackInfo;
import org.apache.ambari.server.state.stack.OsFamily;

import org.apache.ambari.server.state.stack.upgrade.RepositoryVersionHelper;
import org.easymock.Capture;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;

import javax.persistence.EntityManager;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;


/**
 * {@link UpgradeCatalog212} unit tests.
 */
// TODO AMBARI-12698, by the time this gets committed, it will be 2.1.3 instead of 2.1.2
public class UpgradeCatalog212Test {

  private Injector injector;
  private Provider<EntityManager> entityManagerProvider = createStrictMock(Provider.class);
  private EntityManager entityManager = createNiceMock(EntityManager.class);
  private AmbariManagementController amc = createNiceMock(AmbariManagementController.class);
  private AmbariMetaInfo metaInfo = createNiceMock(AmbariMetaInfo.class);
  private StackDAO stackDAO = createNiceMock(StackDAO.class);
  private RepositoryVersionDAO repositoryVersionDAO = createNiceMock(RepositoryVersionDAO.class);
  private ClusterVersionDAO clusterVersionDAO = createNiceMock(ClusterVersionDAO.class);
  private HostVersionDAO hostVersionDAO = createNiceMock(HostVersionDAO.class);
  private ClusterDAO clusterDAO = createNiceMock(ClusterDAO.class);

  @Before
  public void init() {
    reset(entityManagerProvider);
    expect(entityManagerProvider.get()).andReturn(entityManager).anyTimes();
    replay(entityManagerProvider);
    injector = Guice.createInjector(new InMemoryDefaultTestModule());
    injector.getInstance(GuiceJpaInitializer.class);
  }

  @After
  public void tearDown() {
    injector.getInstance(PersistService.class).stop();
  }

  @Test
  public void testExecuteDDLUpdates() throws Exception {
    final DBAccessor dbAccessor = createNiceMock(DBAccessor.class);
    UpgradeCatalog212 upgradeCatalog = (UpgradeCatalog212) getUpgradeCatalog(dbAccessor);

    upgradeCatalog.executeDDLUpdates();
  }

  @Test
  public void testExecuteDMLUpdates() throws Exception {
    final DBAccessor dbAccessor = createNiceMock(DBAccessor.class);
    Configuration configuration = createNiceMock(Configuration.class);
    Connection connection = createNiceMock(Connection.class);
    Statement statement = createNiceMock(Statement.class);
    ResultSet resultSet = createNiceMock(ResultSet.class);
    expect(configuration.getDatabaseUrl()).andReturn(Configuration.JDBC_IN_MEMORY_URL).anyTimes();
    dbAccessor.getConnection();
    expectLastCall().andReturn(connection).anyTimes();
    connection.createStatement();
    expectLastCall().andReturn(statement).anyTimes();
    statement.executeQuery(anyObject(String.class));
    expectLastCall().andReturn(resultSet).anyTimes();

    // Technically, this is a DDL, but it has to be ran during the DML portion
    // because it requires the persistence layer to be started.
    UpgradeSectionDDL upgradeSectionDDL = new UpgradeSectionDDL();

    // Execute any DDL schema changes
    upgradeSectionDDL.execute(dbAccessor);

    // Begin DML verifications
    verifyBootstrapHDP21();

    // Replay main sections
    replay(dbAccessor, configuration, resultSet, connection, statement);

    AbstractUpgradeCatalog upgradeCatalog = getUpgradeCatalog(dbAccessor);
    Class<?> c = AbstractUpgradeCatalog.class;
    Field f = c.getDeclaredField("configuration");
    f.setAccessible(true);
    f.set(upgradeCatalog, configuration);

    upgradeCatalog.executeDMLUpdates();
    verify(dbAccessor, configuration, resultSet, connection, statement);

    // Verify sections
    upgradeSectionDDL.verify(dbAccessor);
  }

  /**
   * Verify that when bootstrapping HDP 2.1, records get inserted into the
   * repo_version, cluster_version, and host_version tables.
   * @throws AmbariException
   */
  private void verifyBootstrapHDP21() throws Exception, AmbariException {
    final String stackName = "HDP";
    final String stackVersion = "2.1";
    final String stackNameAndVersion = stackName + "-" + stackVersion;
    final String buildNumber = "2.1.0.0-0001";
    final String stackAndBuild = stackName + "-" + buildNumber;
    final String clusterName = "c1";

    expect(amc.getAmbariMetaInfo()).andReturn(metaInfo);

    // Mock the actions to bootstrap if using HDP 2.1
    Clusters clusters = createNiceMock(Clusters.class);
    expect(amc.getClusters()).andReturn(clusters);

    Map<String, Cluster> clusterHashMap = new HashMap<String, Cluster>();
    Cluster cluster = createNiceMock(Cluster.class);
    clusterHashMap.put(clusterName, cluster);
    expect(clusters.getClusters()).andReturn(clusterHashMap);

    StackId stackId = new StackId(stackNameAndVersion);
    expect(cluster.getCurrentStackVersion()).andReturn(stackId);

    StackInfo stackInfo = new StackInfo();
    stackInfo.setVersion(buildNumber);
    expect(metaInfo.getStack(stackName, stackVersion)).andReturn(stackInfo);

    StackEntity stackEntity = createNiceMock(StackEntity.class);
    expect(stackEntity.getStackName()).andReturn(stackName);
    expect(stackEntity.getStackVersion()).andReturn(stackVersion);

    expect(stackDAO.find(stackName, stackVersion)).andReturn(stackEntity);

    replay(amc, metaInfo, clusters, cluster, stackEntity, stackDAO);

    // Mock more function calls
    // Repository Version
    RepositoryVersionEntity repositoryVersionEntity = createNiceMock(RepositoryVersionEntity.class);
    expect(repositoryVersionDAO.findByDisplayName(stackAndBuild)).andReturn(null);
    expect(repositoryVersionDAO.findAll()).andReturn(Collections.<RepositoryVersionEntity>emptyList());
    expect(repositoryVersionDAO.create(anyObject(StackEntity.class), anyObject(String.class), anyObject(String.class), anyObject(String.class))).andReturn(repositoryVersionEntity);
    expect(repositoryVersionEntity.getId()).andReturn(1L);
    expect(repositoryVersionEntity.getVersion()).andReturn(buildNumber);
    replay(repositoryVersionDAO, repositoryVersionEntity);

    // Cluster Version
    ClusterVersionEntity clusterVersionEntity = createNiceMock(ClusterVersionEntity.class);
    expect(clusterVersionEntity.getId()).andReturn(1L);
    expect(clusterVersionEntity.getState()).andReturn(RepositoryVersionState.CURRENT);
    expect(clusterVersionEntity.getRepositoryVersion()).andReturn(repositoryVersionEntity);

    expect(clusterVersionDAO.findByClusterAndStackAndVersion(anyObject(String.class), anyObject(StackId.class), anyObject(String.class))).andReturn(null);
    expect(clusterVersionDAO.findAll()).andReturn(Collections.<ClusterVersionEntity>emptyList());
    expect(clusterVersionDAO.create(anyObject(ClusterEntity.class), anyObject(RepositoryVersionEntity.class), anyObject(RepositoryVersionState.class), anyLong(), anyLong(), anyObject(String.class))).andReturn(clusterVersionEntity);
    replay(clusterVersionDAO, clusterVersionEntity);

    // Host Version
    ClusterEntity clusterEntity = createNiceMock(ClusterEntity.class);
    expect(clusterEntity.getClusterName()).andReturn(clusterName).anyTimes();
    expect(clusterDAO.findByName(anyObject(String.class))).andReturn(clusterEntity);

    Collection<HostEntity> hostEntities = new ArrayList<HostEntity>();
    HostEntity hostEntity1 = createNiceMock(HostEntity.class);
    HostEntity hostEntity2 = createNiceMock(HostEntity.class);
    expect(hostEntity1.getHostName()).andReturn("host1");
    expect(hostEntity2.getHostName()).andReturn("host2");
    hostEntities.add(hostEntity1);
    hostEntities.add(hostEntity2);
    expect(clusterEntity.getHostEntities()).andReturn(hostEntities);

    expect(hostVersionDAO.findByClusterStackVersionAndHost(anyObject(String.class), anyObject(StackId.class), anyObject(String.class), anyObject(String.class))).andReturn(null);
    expect(hostVersionDAO.findAll()).andReturn(Collections.<HostVersionEntity>emptyList());

    replay(clusterEntity, clusterDAO, hostVersionDAO, hostEntity1, hostEntity2);
  }

  @Test
  public void testGetTargetVersion() throws Exception {
    final DBAccessor dbAccessor = createNiceMock(DBAccessor.class);
    UpgradeCatalog upgradeCatalog = getUpgradeCatalog(dbAccessor);
    Assert.assertEquals("2.1.2", upgradeCatalog.getTargetVersion());
  }

  @Test
  public void testGetSourceVersion() {
    final DBAccessor dbAccessor = createNiceMock(DBAccessor.class);
    UpgradeCatalog upgradeCatalog = getUpgradeCatalog(dbAccessor);
    Assert.assertEquals("2.1.1", upgradeCatalog.getSourceVersion());
  }

  private AbstractUpgradeCatalog getUpgradeCatalog(final DBAccessor dbAccessor) {
    Module module = new Module() {
      @Override
      public void configure(Binder binder) {
        binder.bind(DBAccessor.class).toInstance(dbAccessor);
        binder.bind(EntityManager.class).toInstance(entityManager);
        binder.bind(OsFamily.class).toInstance(createNiceMock(OsFamily.class));
        binder.bind(ClusterDAO.class).toInstance(clusterDAO);
        binder.bind(RepositoryVersionHelper.class).toInstance(createNiceMock(RepositoryVersionHelper.class));
        binder.bind(Clusters.class).toInstance(createNiceMock(Clusters.class));
        binder.bind(AmbariManagementController.class).toInstance(amc);
        binder.bind(AmbariMetaInfo.class).toInstance(metaInfo);
        binder.bind(StackManagerFactory.class).toInstance(createNiceMock(StackManagerFactory.class));
        binder.bind(StackDAO.class).toInstance(stackDAO);
        binder.bind(RepositoryVersionDAO.class).toInstance(repositoryVersionDAO);
        binder.bind(ClusterVersionDAO.class).toInstance(clusterVersionDAO);
        binder.bind(HostVersionDAO.class).toInstance(hostVersionDAO);
      }
    };
    Injector injector = Guice.createInjector(module);
    return injector.getInstance(UpgradeCatalog212.class);
  }

  // *********** Inner Classes that represent sections of the DDL ***********
  // ************************************************************************

  /**
   * Verify that the upgrade table has two columns added to it.
   */
  class UpgradeSectionDDL implements SectionDDL {

    Capture<DBAccessor.DBColumnInfo> upgradeTablePackageNameColumnCapture = new Capture<DBAccessor.DBColumnInfo>();
    Capture<DBAccessor.DBColumnInfo> upgradeTableUpgradeTypeColumnCapture = new Capture<DBAccessor.DBColumnInfo>();

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute(DBAccessor dbAccessor) throws SQLException {
      // Add columns
      dbAccessor.addColumn(eq("upgrade"), capture(upgradeTablePackageNameColumnCapture));
      dbAccessor.addColumn(eq("upgrade"), capture(upgradeTableUpgradeTypeColumnCapture));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void verify(DBAccessor dbAccessor) throws SQLException {
      // Verification section
      DBAccessor.DBColumnInfo packageNameCol = upgradeTablePackageNameColumnCapture.getValue();
      Assert.assertEquals(String.class, packageNameCol.getType());
      Assert.assertEquals("upgrade_package", packageNameCol.getName());

      DBAccessor.DBColumnInfo upgradeTypeCol = upgradeTableUpgradeTypeColumnCapture.getValue();
      Assert.assertEquals(String.class, upgradeTypeCol.getType());
      Assert.assertEquals("upgrade_type", upgradeTypeCol.getName());
    }
  }
}
