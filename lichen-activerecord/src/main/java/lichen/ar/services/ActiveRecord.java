// Copyright 2013 the original author or authors.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package lichen.ar.services;

/**
 * active record基础类.
 * @author jcai
 */
public abstract class ActiveRecord {
    private String tableName;
    private String pkField = "id";
    public final Long save() {
        return -1L;
    }
    public final int delete() {
        return 1;
    }
    public final void setPkField(final String vpkField) {
        this.pkField = vpkField;
    }
    public final String getPkField() {
        return pkField;
    }
    public final void setTableName(final String vtableName) {
        this.tableName = vtableName;
    }
    public final String getTableName() {
        return tableName;
    }
}
