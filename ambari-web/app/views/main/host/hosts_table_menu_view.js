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

var App = require('app');
var O = Em.Object;

/**
 * @class HostTableMenuView
 */
App.HostTableMenuView = Em.View.extend({

  templateName: require('templates/main/host/bulk_operation_menu'),

  controllerBinding: 'App.router.bulkOperationsController',

  menuItems: function () {
    return {
      s: {label: Em.I18n.t('hosts.table.menu.l1.selectedHosts')},
      f: {label: Em.I18n.t('hosts.table.menu.l1.filteredHosts')},
      a: {label: Em.I18n.t('hosts.table.menu.l1.allHosts')}
    };
  }.property('App.router.clusterController.isLoaded'),

  components: function () {
    var serviceNames = App.Service.find().mapProperty('serviceName');
    var menuItems = [
      O.create({
        serviceName: 'HDFS',
        componentName: 'DATANODE',
        masterComponentName: 'NAMENODE',
        componentNameFormatted: Em.I18n.t('dashboard.services.hdfs.datanodes')
      }),
      O.create({
        serviceName: 'YARN',
        componentName: 'NODEMANAGER',
        masterComponentName: 'RESOURCEMANAGER',
        componentNameFormatted: Em.I18n.t('dashboard.services.yarn.nodeManagers')
      }),
      O.create({
        serviceName: 'HBASE',
        componentName: 'HBASE_REGIONSERVER',
        masterComponentName: 'HBASE_MASTER',
        componentNameFormatted: Em.I18n.t('dashboard.services.hbase.regionServers')
      }),
      O.create({
        serviceName: 'STORM',
        componentName: 'SUPERVISOR',
        masterComponentName: 'SUPERVISOR',
        componentNameFormatted: Em.I18n.t('dashboard.services.storm.supervisors')
      })];

    return menuItems.filter(function (item) {
      return serviceNames.contains(item.serviceName);
    });
  }.property(),

  /**
   * slaveItemView build second-level menu
   * for slave component
   */

  slaveItemView: Em.View.extend({

    tagName: 'li',

    classNames: ['dropdown-submenu'],

    /**
     * Get third-level menu items ingo for slave components
     * operationData format:
     * <code>
     *  {
     *    action: 'STARTED|INSTALLED|RESTART|DECOMMISSION|DECOMMISSION_OFF', // action for selected host components
     *    message: 'some text', // just text to BG popup
     *    componentName: 'DATANODE|NODEMANAGER...', //component name that should be processed
     *    realComponentName: 'DATANODE|NODEMANAGER...', // used only for decommission(_off) actions
     *    serviceName: 'HDFS|YARN|HBASE...', // service name of the processed component
     *    componentNameFormatted: 'DataNodes|NodeManagers...' // "user-friendly" string with component name (used in BG popup)
     *  }
     *  </code>
     *
     * @returns {Array}
     */

    operationsInfo: function () {
      var content = this.get('content');
      var menuItems = Em.A();
      if (App.isAuthorized("SERVICE.START_STOP")) {
        menuItems.pushObjects([
          O.create({
            label: Em.I18n.t('common.start'),
            operationData: O.create({
              action: App.HostComponentStatus.started,
              message: Em.I18n.t('common.start'),
              componentName: content.componentName,
              serviceName: content.serviceName,
              componentNameFormatted: content.componentNameFormatted
            })
          }),
          O.create({
            label: Em.I18n.t('common.stop'),
            operationData: O.create({
              action: App.HostComponentStatus.stopped,
              message: Em.I18n.t('common.stop'),
              componentName: content.componentName,
              serviceName: content.serviceName,
              componentNameFormatted: content.componentNameFormatted
            })
          }),
          O.create({
            label: Em.I18n.t('common.restart'),
            operationData: O.create({
              action: 'RESTART',
              message: Em.I18n.t('common.restart'),
              componentName: content.componentName,
              serviceName: content.serviceName,
              componentNameFormatted: content.componentNameFormatted
            })
          })
        ])
      }
      if (App.isAuthorized("SERVICE.DECOMMISSION_RECOMMISSION") && App.get('components.decommissionAllowed').contains(content.componentName)) {
        menuItems.pushObjects([
          O.create({
            label: Em.I18n.t('common.decommission'),
            decommission: true,
            operationData: O.create({
              action: 'DECOMMISSION',
              message: Em.I18n.t('common.decommission'),
              componentName: content.masterComponentName,
              realComponentName: content.componentName,
              serviceName: content.serviceName,
              componentNameFormatted: content.componentNameFormatted
            })
          }),
          O.create({
            label: Em.I18n.t('common.recommission'),
            decommission: true,
            operationData: O.create({
              action: 'DECOMMISSION_OFF',
              message: Em.I18n.t('common.recommission'),
              componentName: content.masterComponentName,
              realComponentName: content.componentName,
              serviceName: content.serviceName,
              componentNameFormatted: content.componentNameFormatted
            })
          })
        ]);
      }
      return menuItems;
    }.property("content"),

    /**
     * commonOperationView is used for third-level menu items
     * for simple operations ('START','STOP','RESTART')
     */
    commonOperationView: Em.View.extend({
      tagName: 'li',

      click: function () {
        this.get('controller').bulkOperationConfirm(this.get('content'), this.get('selection'));
      }
    }),

    /**
     * advancedOperationView is used for third level menu item
     * for advanced operations ('RECOMMISSION','DECOMMISSION')
     */
    advancedOperationView: Em.View.extend({
      tagName: 'li',
      rel: 'menuTooltip',
      classNameBindings: ['disabledElement'],
      attributeBindings: ['tooltipMsg:data-original-title'],

      service: function () {
        return App.router.get('mainServiceController.content').findProperty('serviceName', this.get('content.serviceName'))
      }.property('App.router.mainServiceController.content.@each', 'content'),

      tooltipMsg: function () {
        return (this.get('disabledElement') == 'disabled') ?
          Em.I18n.t('hosts.decommission.tooltip.warning').format(this.get('content.message'), App.format.role(this.get('content.componentName'))) : '';
      }.property('disabledElement', 'content.componentName'),

      disabledElement: function () {
        return this.get('service.workStatus') == 'STARTED' ? '' : 'disabled';
      }.property('service.workStatus'),

      click: function () {
        if (this.get('disabledElement') == 'disabled') {
          return;
        }
        this.get('controller').bulkOperationConfirm(this.get('content'), this.get('selection'));
      },

      didInsertElement: function () {
        App.tooltip($(this.get('element')));
      }
    })
  }),

  /**
   * hostItemView build second-level menu
   * for host
   */

  hostItemView: Em.View.extend({

    tagName: 'li',

    classNames: ['dropdown-submenu'],

    label: Em.I18n.t('common.hosts'),

    /** Get third-level menu items for Hosts
     * operationData format:
     * <code>
     *  {
     *    action: 'STARTED|INSTALLED|RESTART..', // action for selected hosts (will be applied for each host component in selected hosts)
     *    actionToCheck: 'INSTALLED|STARTED..' // state to filter host components should be processed
     *    message: 'some text', // just text to BG popup
     *  }
     *  </code>
     * @returns {Array}
     */
    operationsInfo: function () {
      var result = [];
      if (App.isAuthorized("SERVICE.START_STOP")) {
        result = result.concat([
          O.create({
            label: Em.I18n.t('hosts.host.details.startAllComponents'),
            operationData: O.create({
              action: 'STARTED',
              actionToCheck: 'INSTALLED',
              message: Em.I18n.t('hosts.host.details.startAllComponents')
            })
          }),
          O.create({
            label: Em.I18n.t('hosts.host.details.stopAllComponents'),
            operationData: O.create({
              action: 'INSTALLED',
              actionToCheck: 'STARTED',
              message: Em.I18n.t('hosts.host.details.stopAllComponents')
            })
          }),
          O.create({
            label: Em.I18n.t('hosts.table.menu.l2.restartAllComponents'),
            operationData: O.create({
              action: 'RESTART',
              message: Em.I18n.t('hosts.table.menu.l2.restartAllComponents')
            })
          })
        ]);
      }

      if (App.isAuthorized("HOST.TOGGLE_MAINTENANCE")) {
        result = result.concat([
          O.create({
            label: Em.I18n.t('passiveState.turnOn'),
            operationData: O.create({
              state: 'ON',
              action: 'PASSIVE_STATE',
              message: Em.I18n.t('passiveState.turnOnFor').format('hosts')
            })
          }),
          O.create({
            label: Em.I18n.t('passiveState.turnOff'),
            operationData: O.create({
              state: 'OFF',
              action: 'PASSIVE_STATE',
              message: Em.I18n.t('passiveState.turnOffFor').format('hosts')
            })
          })
        ]);
      }
      result = result.concat(O.create({
        label: Em.I18n.t('hosts.host.details.setRackId'),
        operationData: O.create({
          action: 'SET_RACK_INFO',
          message: Em.I18n.t('hosts.host.details.setRackId').format('hosts')
        })
      }));
      return result;
    }.property(),

    /**
     * commonOperationView is used for third-level menu items
     * for all operations for host
     */
    operationView: Em.View.extend({
      tagName: 'li',

      click: function () {
        this.get('controller').bulkOperationConfirm(this.get('content'), this.get('selection'));
      }
    })
  })

});
