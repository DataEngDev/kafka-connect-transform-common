/**
 * Copyright © 2017 Jeremy Custenborder (jcustenborder@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.jcustenborder.kafka.connect.transform.common;

import com.github.jcustenborder.kafka.connect.utils.config.Description;
import com.github.jcustenborder.kafka.connect.utils.config.DocumentationTip;
import com.github.jcustenborder.kafka.connect.utils.config.Title;
import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.connect.connector.ConnectRecord;
import org.apache.kafka.connect.data.Field;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaAndValue;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.apache.kafka.connect.data.Struct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;

public abstract class PatternRename<R extends ConnectRecord<R>> extends BaseTransformation<R> {
  private static final Logger log = LoggerFactory.getLogger(PatternRename.class);

  @Override
  public ConfigDef config() {
    return PatternRenameConfig.config();
  }

  PatternRenameConfig config;

  @Override
  public void configure(Map<String, ?> settings) {
    this.config = new PatternRenameConfig(settings);
  }

  @Override
  public void close() {

  }

  @Override
  protected SchemaAndValue processStruct(R record, Schema inputSchema, Struct inputStruct) {
    final SchemaBuilder outputSchemaBuilder = SchemaBuilder.struct();
    outputSchemaBuilder.name(inputSchema.name());
    outputSchemaBuilder.doc(inputSchema.doc());
    if (null != inputSchema.defaultValue()) {
      outputSchemaBuilder.defaultValue(inputSchema.defaultValue());
    }
    if (null != inputSchema.parameters() && !inputSchema.parameters().isEmpty()) {
      outputSchemaBuilder.parameters(inputSchema.parameters());
    }
    if (inputSchema.isOptional()) {
      outputSchemaBuilder.optional();
    }
    Map<String, String> fieldMappings = new HashMap<>(inputSchema.fields().size());
    for (final Field inputField : inputSchema.fields()) {
      log.trace("process() - Processing field '{}'", inputField.name());
      final Matcher fieldMatcher = this.config.pattern.matcher(inputField.name());
      final String outputFieldName;
      if (fieldMatcher.find()) {
        outputFieldName = fieldMatcher.replaceAll(this.config.replacement);
      } else {
        outputFieldName = inputField.name();
      }
      log.trace("process() - Mapping field '{}' to '{}'", inputField.name(), outputFieldName);
      fieldMappings.put(inputField.name(), outputFieldName);
      outputSchemaBuilder.field(outputFieldName, inputField.schema());
    }
    final Schema outputSchema = outputSchemaBuilder.build();
    final Struct outputStruct = new Struct(outputSchema);
    for (Map.Entry<String, String> entry : fieldMappings.entrySet()) {
      final String inputField = entry.getKey(), outputField = entry.getValue();
      log.trace("process() - Copying '{}' to '{}'", inputField, outputField);
      final Object value = inputStruct.get(inputField);
      outputStruct.put(outputField, value);
    }
    return new SchemaAndValue(outputSchema, outputStruct);
  }

  @Override
  protected SchemaAndValue processMap(R record, Map<String, Object> input) {
    final Map<String, Object> outputMap = new LinkedHashMap<>(input.size());

    for (final String inputFieldName : input.keySet()) {
      log.trace("process() - Processing field '{}'", inputFieldName);
      final Matcher fieldMatcher = this.config.pattern.matcher(inputFieldName);
      final String outputFieldName;
      if (fieldMatcher.find()) {
        outputFieldName = fieldMatcher.replaceAll(this.config.replacement);
      } else {
        outputFieldName = inputFieldName;
      }
      final Object value = input.get(inputFieldName);
      outputMap.put(outputFieldName, value);
    }
    return new SchemaAndValue(null, outputMap);
  }

  @Title("PatternRename(Key)")
  @Description("This transformation is used to rename fields in the key of an input struct based on a regular expression and a replacement string.")
  @DocumentationTip("This transformation is used to manipulate fields in the Key of the record.")
  public static class Key<R extends ConnectRecord<R>> extends PatternRename<R> {

    @Override
    public R apply(R r) {
      final SchemaAndValue transformed = process(r, r.keySchema(), r.key());

      return r.newRecord(
          r.topic(),
          r.kafkaPartition(),
          transformed.schema(),
          transformed.value(),
          r.valueSchema(),
          r.value(),
          r.timestamp()
      );
    }
  }

  @Title("PatternRename(Value)")
  @Description("This transformation is used to rename fields in the value of an input struct based on a regular expression and a replacement string.")
  public static class Value<R extends ConnectRecord<R>> extends PatternRename<R> {
    @Override
    public R apply(R r) {
      final SchemaAndValue transformed = process(r, r.valueSchema(), r.value());

      return r.newRecord(
          r.topic(),
          r.kafkaPartition(),
          r.keySchema(),
          r.key(),
          transformed.schema(),
          transformed.value(),
          r.timestamp()
      );
    }
  }
}
