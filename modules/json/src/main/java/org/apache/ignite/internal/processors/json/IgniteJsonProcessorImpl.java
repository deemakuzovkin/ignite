/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ignite.internal.processors.json;

import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonString;
import javax.json.JsonValue;
import org.apache.ignite.IgniteException;
import org.apache.ignite.binary.BinaryObject;
import org.apache.ignite.internal.GridKernalContext;
import org.apache.ignite.internal.processors.GridProcessorAdapter;
import org.apache.ignite.internal.processors.cache.CacheObject;
import org.apache.ignite.internal.processors.cache.CacheObjectContext;
import org.apache.ignite.internal.processors.cache.KeyCacheObject;
import org.jetbrains.annotations.Nullable;

/**
 * Ignite JSON objects processor.
 */
public class IgniteJsonProcessorImpl extends GridProcessorAdapter implements IgniteJsonProcessor {
    /**
     * @param ctx Context.
     */
    public IgniteJsonProcessorImpl(GridKernalContext ctx) {
        super(ctx);
    }

    /** {@inheritDoc} */
    @Override public KeyCacheObject toCacheKeyObject(CacheObjectContext ctx, Object obj, boolean userObj) {
        return (KeyCacheObject)toCacheObject(ctx, obj, userObj);
    }

    /** {@inheritDoc} */
    @Nullable @Override public CacheObject toCacheObject(CacheObjectContext ctx,
        @Nullable Object obj,
        boolean userObj) {
        if (obj instanceof JsonObject) {
            IgniteJsonObject jsonObj = (IgniteJsonObject)obj;

            BinaryObject binObj = jsonObj.binaryObject();

            return (CacheObject)binObj;
        }

        return null;
    }

    /** {@inheritDoc} */
    @Override public boolean jsonObject(Object obj) {
        return obj instanceof JsonObject || obj instanceof BinaryObject &&
            ((BinaryObject)obj).type().typeName().equals(JsonObject.class.getName());
    }

    /** {@inheritDoc} */
    @Override public Object value(Object obj) {
        if (obj instanceof BinaryObject) {
            assert ((BinaryObject)obj).type().typeName().equals(JsonObject.class.getName()) : obj;

            return new IgniteJsonObject((BinaryObject) obj);
        }

        return null;
    }

    /**
     * @param obj Object.
     * @param fieldName Field name.
     * @return Field value.
     */
    static Object value(JsonObject obj, String fieldName) {
        JsonValue jsonVal = obj.get(fieldName);

        if (jsonVal == null)
            return null;

        switch (jsonVal.getValueType()) {
            case FALSE:
                return Boolean.FALSE;

            case TRUE:
                return Boolean.TRUE;

            case STRING:
                return ((JsonString)jsonVal).getString();

            case NUMBER:
                return ((JsonNumber)jsonVal).intValue();

            case OBJECT:
                return jsonVal;

            default:
                throw new IgniteException("Unsupported type [field=" + fieldName +
                    ", type=" + jsonVal.getValueType() + ']');
        }
    }
}
