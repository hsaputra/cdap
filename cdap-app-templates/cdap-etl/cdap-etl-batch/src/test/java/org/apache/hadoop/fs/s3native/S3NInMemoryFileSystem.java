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

package org.apache.hadoop.fs.s3native;

/**
 * TODO: [CDAP-2824] - Delete this class when Hadoop dependency is updated to 2.6.0
 * Used for testing File without actually connecting to an File instance.
 */
public class S3NInMemoryFileSystem extends NativeS3FileSystem {
  public S3NInMemoryFileSystem() {
    super(new InMemoryNativeFileSystemStore());
  }
}