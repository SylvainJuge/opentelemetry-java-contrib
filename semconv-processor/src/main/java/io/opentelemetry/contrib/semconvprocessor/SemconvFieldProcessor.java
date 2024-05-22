package io.opentelemetry.contrib.semconvprocessor;

import com.google.auto.service.AutoService;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;

@AutoService({Processor.class})
@SupportedAnnotationTypes("io.opentelemetry.contrib.semconvprocessor.*")
public class SemconvFieldProcessor extends AbstractProcessor {

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.RELEASE_8;
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {

    Set<? extends Element> annotatedFields = roundEnv.getElementsAnnotatedWith(SemconvField.class);
    for (Element annotatedField : annotatedFields) {

      processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, "sout "+ annotatedField);

      // TODO : check referenced semconv attribute matches and is valid

      // bonus: can we check the value

    }

    return false;
  }
}
