/*
 * Copyright © 2015 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package co.cask.cdap.etl.transform;

import co.cask.cdap.api.data.DatasetContext;
import co.cask.cdap.api.dataset.Dataset;
import co.cask.cdap.api.dataset.lib.ObjectMappedTable;

import java.lang.reflect.Field;

/**
 * Context for {@link ScriptTransform}.
 */
public class ScriptTransformContext {

  private final DatasetContext context;

  public ScriptTransformContext(DatasetContext context) {
    this.context = context;
  }

  public Object lookup(String instance, String key, String column) {
    // TODO: read from explorable dataset
    Dataset dataset = context.getDataset(instance);
    if (!(dataset instanceof ObjectMappedTable)) {
      throw new IllegalArgumentException(String.format("Dataset %s is not an ObjectMappedTable", instance));
    }

    ObjectMappedTable table = (ObjectMappedTable) dataset;
    Object value = table.read(key);
    try {
      Field field = value.getClass().getDeclaredField(column);
      field.setAccessible(true);
      try {
        return field.get(value);
      } finally {
        field.setAccessible(false);
      }
    } catch (NoSuchFieldException e) {
      throw new IllegalArgumentException(String.format("Column %s does not exist", column), e);
    } catch (IllegalAccessException e) {
      throw new IllegalArgumentException(String.format("Column %s cannot be accessed", column), e);
    }
  }

}
