/**
 *    Copyright 2016-2019 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.mybatis.dynamic.sql.render;

import java.util.Optional;

import org.mybatis.dynamic.sql.BindableColumn;

public class MyBatis3RenderingStrategy extends RenderingStrategy {
    @Override
    public String getFormattedJdbcPlaceholder(Optional<BindableColumn<?>> column, String prefix, String parameterName) {
        return "#{" //$NON-NLS-1$
                + prefix
                + "." //$NON-NLS-1$
                + parameterName
                + renderJdbcType(column)
                + renderTypeHandler(column)
                + "}"; //$NON-NLS-1$
    }
    
    private String renderTypeHandler(Optional<BindableColumn<?>> column) {
        return column
                .flatMap(BindableColumn::typeHandler)
                .map(th -> ",typeHandler=" + th) //$NON-NLS-1$
                .orElse(""); //$NON-NLS-1$
    }
    
    private String renderJdbcType(Optional<BindableColumn<?>> column) {
        return column
                .flatMap(BindableColumn::jdbcType)
                .map(jt -> ",jdbcType=" + jt.getName()) //$NON-NLS-1$
                .orElse(""); //$NON-NLS-1$
    }
}
