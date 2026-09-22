/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.agentscope.core.tool;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to describe parameters of a tool method.
 *
 * <p>This annotation is required for all parameters of methods annotated with {@link Tool} (except
 * {@link ToolEmitter} which is auto-injected). It provides metadata for generating JSON schemas
 * that describe the tool's parameters to LLMs.
 *
 * <p><b>Usage Example:</b>
 *
 * <pre>{@code
 * @Tool(name = "calculate_area", description = "Calculate rectangle area")
 * public double calculateArea(
 *     @ToolParam(name = "width", description = "Width in meters", required = true)
 *     double width,
 *     @ToolParam(name = "height", description = "Height in meters", required = true)
 *     double height,
 *     @ToolParam(name = "unit", description = "Unit of measurement", required = false)
 *     String unit
 * ) {
 *     // Implementation
 * }
 * }</pre>
 *
 * <p><b>Important Notes:</b>
 * <ul>
 *   <li>The {@code name} attribute is <b>required</b> because Java does not preserve parameter
 *       names at runtime by default</li>
 *   <li>Parameter names should follow snake_case convention for LLM compatibility</li>
 *   <li>Descriptions help the LLM understand what values to provide</li>
 *   <li>{@link ToolEmitter} parameters do not need this annotation (they are framework-injected)</li>
 * </ul>
 *
 * <p><b>Generic types:</b> the schema is derived from the parameter's <i>generic</i> type
 * ({@link java.lang.reflect.Parameter#getParameterizedType()}), so type arguments on collections
 * are preserved rather than erased. A {@code List<String>} parameter becomes
 * {@code {"type": "array", "items": {"type": "string"}}}, a {@code List<Item>} becomes an array
 * whose {@code items} is the object schema of {@code Item}, and the {@code description} above is
 * attached to the array property itself rather than to its {@code items}. {@code Map<K, V>} is the
 * exception: it generates a bare {@code {"type": "object"}} with the key and value types left
 * undescribed, so prefer a POJO parameter when the shape is known.
 *
 * @see Tool
 * @see ToolEmitter
 */
@Target({ElementType.PARAMETER, ElementType.FIELD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ToolParam {

    /**
     * The name of the tool parameter.
     *
     * <p><b>This attribute is required</b> because Java does not preserve parameter names at
     * runtime by default (unless compiled with {@code -parameters} flag, which is not reliable).
     * The toolkit uses this name to map LLM-provided arguments to method parameters.
     *
     * <p>Names should follow snake_case convention (e.g., "file_path", "max_results") for
     * compatibility with various LLM providers.
     *
     * @return The parameter name as it should appear in the tool schema
     */
    String name();

    /**
     * Whether this parameter is required.
     *
     * <p>This flag does <b>not</b> control whether the parameter appears in the generated schema:
     * every {@code @ToolParam} parameter is always listed under {@code "properties"}. It only
     * controls whether the parameter name is added to the schema's top-level {@code "required"}
     * array. When no parameter is required, the {@code "required"} key is omitted from the schema
     * entirely rather than emitted as an empty array.
     *
     * <p>Arguments are validated against the generated schema before the method is invoked, so a
     * missing required parameter is reported back to the LLM and the method is never entered.
     *
     * <p>An optional parameter that the LLM omits is passed to the method as {@code null}. Declare
     * optional parameters with boxed types ({@code Integer}, {@code Double}, {@code Boolean})
     * rather than primitives: {@code null} cannot be passed to an {@code int} or {@code double},
     * so a primitive parameter marked {@code required = false} fails at invocation time when the
     * LLM omits it.
     *
     * <p>On the fields of a POJO parameter this flag behaves the same way, except that a field
     * <i>without</i> {@code @ToolParam} is still part of the schema as an optional property, while
     * a method parameter without {@code @ToolParam} is excluded from the schema altogether.
     *
     * @return true if required (default), false if optional
     */
    boolean required() default true;

    /**
     * The description of this parameter.
     *
     * <p>This description is sent to the LLM as part of the tool schema to help it understand:
     * <ul>
     *   <li>What this parameter represents</li>
     *   <li>What format or values are expected</li>
     *   <li>Any constraints or validation rules</li>
     * </ul>
     *
     * <p>Good descriptions improve the LLM's ability to provide correct parameter values.
     *
     * @return The parameter description, or empty string if not provided
     */
    String description() default "";
}
