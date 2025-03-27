package org.springdoc.core.utils;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.validation.constraints.*;
import kotlin.reflect.KProperty;
import kotlin.reflect.jvm.ReflectJvmMapping;
import org.springframework.core.KotlinDetector;
import org.springframework.lang.Nullable;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

import static org.springdoc.core.utils.Constants.OPENAPI_ARRAY_TYPE;
import static org.springdoc.core.utils.Constants.OPENAPI_STRING_TYPE;

/**
 * The type Validation utils.
 *
 * @author dyun
 */
public class SchemaUtils {

	/**
	 * The constructor.
	 */
	private SchemaUtils() {
	}

	/**
	 * The constant JAVA_FIELD_NULLABLE_DEFAULT.
	 */
	public static Boolean JAVA_FIELD_NULLABLE_DEFAULT = true;

	/**
	 * The constant OPTIONAL_TYPES.
	 */
	private static final Set<Class<?>> OPTIONAL_TYPES = new HashSet<>();

	static {
		OPTIONAL_TYPES.add(Optional.class);
		OPTIONAL_TYPES.add(OptionalInt.class);
		OPTIONAL_TYPES.add(OptionalLong.class);
		OPTIONAL_TYPES.add(OptionalDouble.class);
	}

	/**
	 * The constant ANNOTATIONS_FOR_REQUIRED.
	 */
	// using string litterals to support both validation-api v1 and v2
	public static final List<String> ANNOTATIONS_FOR_REQUIRED = Arrays.asList("NotNull", "NonNull", "NotBlank",
			"NotEmpty");

	/**
	 * Is swagger visible.
	 * @param schema the schema
	 * @param parameter the parameter
	 * @return the boolean
	 */
	public static boolean swaggerVisible(@Nullable io.swagger.v3.oas.annotations.media.Schema schema,
			@Nullable Parameter parameter) {
		if (parameter != null) {
			return !parameter.hidden();
		}
		if (schema != null) {
			return !schema.hidden();
		}
		return true;
	}

	/**
	 * Is swagger required. it may return {@code null} if not specified require value or
	 * mode
	 * @param schema the schema
	 * @param parameter the parameter
	 * @return the boolean or {@code null}
	 * @see io.swagger.v3.oas.annotations.Parameter#required()
	 * @see io.swagger.v3.oas.annotations.media.Schema#required()
	 * @see io.swagger.v3.oas.annotations.media.Schema#requiredMode()
	 */
	@Nullable
	public static Boolean swaggerRequired(@Nullable io.swagger.v3.oas.annotations.media.Schema schema,
			@Nullable Parameter parameter) {
		if (parameter != null && parameter.required()) {
			return true;
		}
		if (schema != null) {
			if (schema.required()
					|| schema.requiredMode() == io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED) {
				return true;
			}
			if (schema.requiredMode() == io.swagger.v3.oas.annotations.media.Schema.RequiredMode.NOT_REQUIRED) {
				return false;
			}
		}
		return null;
	}

	/**
	 * Check if the parameter has any of the annotations that make it non-optional
	 * @param annotationSimpleNames the annotation simple class named, e.g. NotNull
	 * @return whether any of the known NotNull annotations are present
	 */
	public static boolean hasNotNullAnnotation(Collection<String> annotationSimpleNames) {
		return ANNOTATIONS_FOR_REQUIRED.stream().anyMatch(annotationSimpleNames::contains);
	}

	/**
	 * Is annotated notnull.
	 * @param annotations the field annotations
	 * @return the boolean
	 */
	public static boolean annotatedNotNull(List<Annotation> annotations) {
		Collection<String> annotationSimpleNames = annotations.stream()
			.map(annotation -> annotation.annotationType().getSimpleName())
			.collect(Collectors.toSet());
		return ANNOTATIONS_FOR_REQUIRED.stream().anyMatch(annotationSimpleNames::contains);
	}

	/**
	 * Is field nullable. java default is nullable
	 * {@link SchemaUtils#JAVA_FIELD_NULLABLE_DEFAULT}
	 * @param field the field
	 * @return the boolean
	 */
	public static boolean fieldNullable(Field field) {
		if (OPTIONAL_TYPES.stream().anyMatch(c -> c.isAssignableFrom(field.getType()))) {
			return true;
		}
		if (KotlinDetector.isKotlinPresent() && KotlinDetector.isKotlinReflectPresent()
				&& KotlinDetector.isKotlinType(field.getDeclaringClass())) {
			KProperty<?> kotlinProperty = ReflectJvmMapping.getKotlinProperty(field);
			if (kotlinProperty != null) {
				return kotlinProperty.getReturnType().isMarkedNullable();
			}
		}
		return JAVA_FIELD_NULLABLE_DEFAULT;
	}

	/**
	 * Is field required.
	 * @param field the field
	 * @param schema the schema
	 * @param parameter the parameter
	 * @return the boolean
	 * @see io.swagger.v3.oas.annotations.Parameter#required()
	 * @see io.swagger.v3.oas.annotations.media.Schema#required()
	 * @see io.swagger.v3.oas.annotations.media.Schema#requiredMode()
	 */
	public static boolean fieldRequired(Field field, @Nullable io.swagger.v3.oas.annotations.media.Schema schema,
			@Nullable Parameter parameter) {
		Boolean swaggerRequired = swaggerRequired(schema, parameter);
		if (swaggerRequired != null) {
			return swaggerRequired;
		}
		boolean annotatedNotNull = annotatedNotNull(Arrays.asList(field.getDeclaredAnnotations()));
		if (annotatedNotNull) {
			return true;
		}
		return !fieldNullable(field);
	}

	/**
	 * Apply validations to schema. the annotation order effects the result of the
	 * validation.
	 * @param schema the schema
	 * @param annotations the annotations
	 */
	public static void applyValidationsToSchema(Schema<?> schema, List<Annotation> annotations) {
		annotations.forEach(anno -> {
			String annotationName = anno.annotationType().getSimpleName();
			if (annotationName.equals(PositiveOrZero.class.getSimpleName())) {
				schema.setMinimum(BigDecimal.ZERO);
			}
			if (annotationName.equals(NegativeOrZero.class.getSimpleName())) {
				schema.setMaximum(BigDecimal.ZERO);
			}
			if (annotationName.equals(Min.class.getSimpleName())) {
				schema.setMinimum(BigDecimal.valueOf(((Min) anno).value()));
			}
			if (annotationName.equals(Max.class.getSimpleName())) {
				schema.setMaximum(BigDecimal.valueOf(((Max) anno).value()));
			}
			if (annotationName.equals(DecimalMin.class.getSimpleName())) {
				DecimalMin min = (DecimalMin) anno;
				if (min.inclusive()) {
					schema.setMinimum(BigDecimal.valueOf(Double.parseDouble(min.value())));
				}
				else {
					schema.setExclusiveMinimum(true);
				}
			}
			if (annotationName.equals(DecimalMax.class.getSimpleName())) {
				DecimalMax max = (DecimalMax) anno;
				if (max.inclusive()) {
					schema.setMaximum(BigDecimal.valueOf(Double.parseDouble(max.value())));
				}
				else {
					schema.setExclusiveMaximum(true);
				}
			}
			if (annotationName.equals(Size.class.getSimpleName())) {
				if (OPENAPI_ARRAY_TYPE.equals(schema.getType())) {
					schema.setMinItems(((Size) anno).min());
					schema.setMaxItems(((Size) anno).max());
				}
				else if (OPENAPI_STRING_TYPE.equals(schema.getType())) {
					schema.setMinLength(((Size) anno).min());
					schema.setMaxLength(((Size) anno).max());
				}
			}
			if (annotationName.equals(Pattern.class.getSimpleName())) {
				schema.setPattern(((Pattern) anno).regexp());
			}
		});
		if (annotatedNotNull(annotations)) {
			String specVersion = schema.getSpecVersion().name();
			if (!"V30".equals(specVersion)) {
				schema.setNullable(false);
			}
		}
	}

}
