package io.opentelemetry.contrib.semconvprocessor;

import com.google.auto.service.AutoService;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic;

@AutoService({Processor.class})
@SupportedAnnotationTypes("io.opentelemetry.contrib.semconvprocessor.*")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class SemconvFieldProcessor extends AbstractProcessor {

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    if (roundEnv.processingOver()) {
      return false;
    }

    for (TypeElement annotationTypeElement : annotations) {
      Set<? extends Element> annotatedElements
          = roundEnv.getElementsAnnotatedWith(annotationTypeElement);

      for (Element annotatedElement : annotatedElements) {

        Element annotatedFieldClassElement = annotatedElement.getEnclosingElement();
        if (!(annotatedFieldClassElement instanceof TypeElement)) {
          error("annotated field must have an enclosing class");
          return false;
        }

        String annotatedClassFqn = ((TypeElement) annotatedFieldClassElement).getQualifiedName()
            .toString();

        String annotatedFieldFqn = annotatedClassFqn + "."
            + annotatedElement.getSimpleName().toString();

        info("annotated field " + annotatedElement);

        // check modifiers of the annotated element
        if (!annotatedElement.getModifiers()
            .containsAll(Arrays.asList(Modifier.STATIC, Modifier.FINAL))) {
          error("annotated field must be static final " + annotatedFieldFqn);
          return false;
        }

        String fieldName = annotatedElement.getSimpleName().toString();
        String fieldClass = null;

        List<? extends AnnotationMirror> annotationMirrors = annotatedElement.getAnnotationMirrors();
        for (AnnotationMirror annotationMirror : annotationMirrors) {
          for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : annotationMirror.getElementValues()
              .entrySet()) {

            String entryKey = entry.getKey().toString();
            String entryValue = entry.getValue().getValue().toString();

            switch (entryKey) {
              case "value()":
                fieldClass = entryValue;
                break;
              case "field()":
                fieldName = entryValue;
                break;
              default:
                error("unexpected annotation attribute " + entryKey);
                fieldName = "";
            }
          }
        }

        if (fieldClass == null) {
          error("unexpected missing target class");
          return false;
        }

        String targetFieldFqn = fieldClass + "." + fieldName;

        TypeElement targetClassType = processingEnv.getElementUtils().getTypeElement(fieldClass);
        List<VariableElement> targetClassFields = ElementFilter.fieldsIn(
            targetClassType.getEnclosedElements());

        VariableElement targetFieldElement = null;
        for (VariableElement field : targetClassFields) {
          if (field.getSimpleName().toString().equals(fieldName)) {
            targetFieldElement = field;
          }
        }

        if (targetFieldElement == null) {
          error("missing target field " + targetFieldFqn);
          return false;
        }

        // check annotated and target field types match
        if (!processingEnv.getTypeUtils()
            .isSameType(annotatedElement.asType(), targetFieldElement.asType())) {

          error(annotatedFieldFqn + " does not match type of " + targetFieldFqn + " "
              + targetFieldElement.asType().toString());
        }

        // TODO: can we check the target value ?

      }

    }

    return false;
  }

  private void error(String msg) {
    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, msg);
  }


  private void info(String msg) {
    processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, msg);
  }
}
