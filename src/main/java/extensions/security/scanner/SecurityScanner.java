package extensions.security.scanner;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import core.terraform.Module;
import core.upload.FormData;
import extensions.cli.CliCommandProcessor;
import extensions.security.report.TfSecReport;
import extensions.security.util.TfSecReportUtil;
import io.quarkus.runtime.annotations.RegisterForReflection;
import io.quarkus.vertx.ConsumeEvent;
import io.smallrye.common.annotation.Blocking;
import io.vertx.mutiny.core.eventbus.EventBus;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Logger;

@RegisterForReflection
@ApplicationScoped
public class SecurityScanner {

  static final Logger LOGGER = Logger.getLogger(SecurityScanner.class.getName());

  EventBus eventBus;
  CliCommandProcessor commandProcessor;
  ObjectMapper mapper = new ObjectMapper();

  public SecurityScanner(EventBus eventBus, CliCommandProcessor commandProcessor) {
    this.eventBus = eventBus;
    this.commandProcessor = commandProcessor;
  }

  @Blocking
  @ConsumeEvent("module.security.report")
  public HashMap<String, List<TfSecReport.TfSecResult>> scanModule(FormData archive) {
    Module module = archive.getEntity();
    LOGGER.info(String.format("Starting scan for module %s, version %s",
            module.getName(),
            module.getCurrentVersion()
    ));
    File workingDirectory = archive.getCompressedFile().getParentFile();
    String output = commandProcessor.runCommand(
            workingDirectory,
            "sh", "-c", "tfsec -f json  --ignore-hcl-errors .");
    TfSecReport tfSecReport;
    try {
      tfSecReport = mapper.readValue(output, TfSecReport.class);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
    HashMap<String, List<TfSecReport.TfSecResult>> securityReport = TfSecReportUtil
            .sanitizeAndGroupAndSortFindings(tfSecReport, workingDirectory.toPath());
    LOGGER.info(String.format("Finished scan for module %s, version %s",
            module.getName(),
            module.getCurrentVersion()
    ));
    return securityReport;
  }
}
