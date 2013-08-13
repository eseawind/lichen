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
package lichen.ar.model;

import lichen.ar.services.FieldType;

/**
 * 针对对象的字段定义.
 * @author jcai
 */
public class Field<T> {
    //字段类型
    private FieldType<T> fieldType;
    //字段名称
    private String fieldName;
    //列名
    private String columnName;

    public final void setFieldType(final FieldType<T> vfieldType) {
        this.fieldType = vfieldType;
    }
    public final FieldType<T> getFieldType() {
        return fieldType;
    }
    public final void setFieldName(final String vfieldName) {
        this.fieldName = vfieldName;
    }
    public final String getFieldName() {
        return fieldName;
    }
    public final void setColumnName(final String vcolumnName) {
        this.columnName = vcolumnName;
    }
    public final String getColumnName() {
        return columnName;
    }
}
