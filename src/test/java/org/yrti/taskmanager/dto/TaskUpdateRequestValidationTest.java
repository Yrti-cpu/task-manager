package org.yrti.taskmanager.dto;

import jakarta.validation.Validation;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.yrti.taskmanager.model.enums.Category;
import org.yrti.taskmanager.model.enums.Status;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TaskUpdateRequestValidationTest {

    @ParameterizedTest(name = "version={0}: valid={1}")
    @MethodSource("versions")
    void validatesVersion(Long version, boolean valid, String constraint) {
        // given
        TaskUpdateRequest request = new TaskUpdateRequest(
                "Write tests", Status.IN_PROGRESS, Category.WORK, null, version);
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();

            // when
            var violations = validator.validate(request);

            // then
            if (valid) {
                assertThat(violations).isEmpty();
            } else {
                assertThat(violations).singleElement().satisfies(violation -> {
                    assertThat(violation.getPropertyPath().toString()).isEqualTo("version");
                    assertThat(violation.getConstraintDescriptor().getAnnotation()
                            .annotationType().getSimpleName()).isEqualTo(constraint);
                });
            }
        }
    }

    static Stream<Arguments> versions() {
        return Stream.of(
                Arguments.of(0L, true, null),
                Arguments.of(-1L, false, "PositiveOrZero"),
                Arguments.of(null, false, "NotNull"));
    }
}
