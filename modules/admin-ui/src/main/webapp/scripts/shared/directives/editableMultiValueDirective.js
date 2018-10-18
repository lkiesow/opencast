/**
 * Licensed to The Apereo Foundation under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 *
 * The Apereo Foundation licenses this file to you under the Educational
 * Community License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License
 * at:
 *
 *   http://opensource.org/licenses/ecl2.txt
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations under
 * the License.
 *
 */
'use strict';

/**
 * @ngdoc directive
 * @name ng.directive:adminNgEditableMultiValue
 *
 * @description
 * Upon click on its label, this directive will display an <input> field and
 * currently selected values. They will be transformed back into a label on blur.
 *
 * @element field
 * The "params" attribute contains an object with the attributes `id`,
 * `required` and `value`.
 * The "save" attribute is a reference to a save function used to persist the
 * values.
 *
 * @example
   <doc:example>
     <doc:source>
      <div admin-ng-editable-multi-value params="params" save="save"></div>
     </doc:source>
   </doc:example>
 */
angular.module('adminNg.directives')
.directive('adminNgEditableMultiValue', ['$timeout', function ($timeout) {
  return {
    restrict: 'A',
    templateUrl: 'shared/partials/editableMultiValue.html',
    replace: true,
    scope: {
      params:     '=',
      save:       '='
    },
    link: function (scope, element) {
      scope.enterEditMode = function () {
        scope.editMode = true;
        scope.focusTimer = $timeout(function () {
          element.find('input').focus();
        });
      };

      scope.leaveEditMode = function () {
        scope.addValue(scope.params.value, scope.value);
        scope.editMode = false;
        scope.value = '';
      };

      scope.addValue = function (model, value) {
        if (value && model.indexOf(value) === -1) {
          model.push(value);
          scope.editMode = false;
        }
        scope.submit();
      };

      scope.removeValue = function (model, value) {
        model.splice(model.indexOf(value), 1);
        scope.submit();
      };

      scope.keyUp = function (event) {
        if (event.keyCode === 13) {
          // ENTER
          scope.addValue(scope.params.value, scope.value);
        } else if (event.keyCode === 27) {
          // ESC
          scope.editMode = false;
        }
        event.stopPropagation();
      };

      scope.submit = function () {
        scope.save(scope.params.id);
        scope.editMode = false;
      };

      scope.$on('$destroy', function () {
        $timeout.cancel(scope.focusTimer);
      });
    }
  };
}]);
