/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.cloudstack.storage.image;

import java.util.List;

import org.apache.cloudstack.engine.subsystem.api.storage.CreateCmdResult;
import org.apache.cloudstack.engine.subsystem.api.storage.DataObject;
import org.apache.cloudstack.engine.subsystem.api.storage.DataStore;
import org.apache.cloudstack.engine.subsystem.api.storage.DataStoreDriver;
import org.apache.cloudstack.engine.subsystem.api.storage.TemplateInfo;
import org.apache.cloudstack.framework.async.AsyncCompletionCallback;

import com.cloud.storage.Storage.ImageFormat;
import com.cloud.utils.Ternary;

public interface ImageStoreDriver extends DataStoreDriver {
    String createEntityExtractUrl(DataStore store, String installPath, ImageFormat format, DataObject dataObject);

    List<Ternary<String, Long, Long>> getDatadiskTemplates(DataObject obj);

    Void createDataDiskTemplateAsync(TemplateInfo dataDiskTemplate, String path, long fileSize, AsyncCompletionCallback<CreateCmdResult> callback);
}
