/*
 * Copyright (c) Facebook, Inc. and its affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.business.cloudbridge.pl.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.concurrent.Semaphore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DeployController {
  private final String NO_UPDATES_MESSAGE = "No updates";

  private final Logger logger = LoggerFactory.getLogger(DeployController.class);

  private DeploymentRunner runner;
  private Semaphore singleProvisioningLock = new Semaphore(1);

  @PostMapping(
      path = "/v1/deployment",
      consumes = "application/json",
      produces = "application/json")
  public APIReturn deploymentCreate(@RequestBody DeploymentParams deployment) {
    logger.info("Received deployment request: " + deployment.toString());

    try {
      deployment.validate();
    } catch (InvalidDeploymentArgumentException ex) {
      return new APIReturn(APIReturn.Status.STATUS_FAIL, ex.getMessage());
    }
    logger.info("  Validated input");

    try {
      if (!singleProvisioningLock.tryAcquire()) {
        String errorMessage = "Another deployment is in progress";
        logger.error("  " + errorMessage);
        return new APIReturn(APIReturn.Status.STATUS_FAIL, errorMessage);
      }
      logger.info("  No deployment conflicts found");

      runner =
          new DeploymentRunner(
              deployment,
              () -> {
                singleProvisioningLock.release();
              });
      runner.start();
      return new APIReturn(APIReturn.Status.STATUS_SUCCESS, "Deployment Started Successfully");
    } catch (DeploymentException ex) {
      return new APIReturn(APIReturn.Status.STATUS_ERROR, ex.getMessage());
    } finally {
      logger.info("  Deployment request finalized");
    }
  }

  @GetMapping(path = "/v1/deployment", produces = "application/json")
  public APIReturn deploymentStatus() {
    logger.info("Received status command");

    if (runner == null
        || runner.getDeploymentState() == DeploymentRunner.DeploymentState.STATE_NOT_STARTED) {
      logger.info("  No deployment is running");
      return new APIReturn(APIReturn.Status.STATUS_SUCCESS, NO_UPDATES_MESSAGE);
    }

    DeploymentRunner.DeploymentState state = runner.getDeploymentState();

    ObjectMapper mapper = new ObjectMapper();
    ObjectNode rootNode = mapper.createObjectNode();
    rootNode.put("state", state.toString());
    if (state == DeploymentRunner.DeploymentState.STATE_FINISHED)
      rootNode.put("exitValue", runner.getExitValue());

    return new APIReturn(APIReturn.Status.STATUS_SUCCESS, runner.getOutput(), rootNode);
  }
}
