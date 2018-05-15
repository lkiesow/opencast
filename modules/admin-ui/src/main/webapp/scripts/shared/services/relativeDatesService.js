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

angular.module('adminNg.services')
.factory('RelativeDatesService', function () {

    var RelativeDatesService = function () {

        moment.locale(navigator.language);

        this.relativeToAbsoluteDate = function(relative, type, from) {

            if (from === true) {
                var absolute = moment().startOf(type);
            }
            else {
                var absolute = moment().endOf(type)
            }

            absolute = absolute.add(relative, type);

            return absolute;
        };

        this.relativeDateToFilterValue = function(dateString, type) {

            var relativeDates = dateString.split("/");

            var countFrom = parseInt(relativeDates[0]);
            var countTo = parseInt(relativeDates[1]);
            var from = this.relativeToAbsoluteDate(countFrom, type, true);
            var to = this.relativeToAbsoluteDate(countTo, type, false);

            return from.toISOString() + "/" + to.toISOString();
        };
    };

    return new RelativeDatesService();

});
