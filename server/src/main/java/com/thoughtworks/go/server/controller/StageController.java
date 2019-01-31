/*
 * Copyright 2018 ThoughtWorks, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.thoughtworks.go.server.controller;

import com.thoughtworks.go.config.*;
import com.thoughtworks.go.config.exceptions.RecordNotFoundException;
import com.thoughtworks.go.server.GoUnauthorizedException;
import com.thoughtworks.go.server.newsecurity.utils.SessionUtils;
import com.thoughtworks.go.server.security.HeaderConstraint;
import com.thoughtworks.go.server.security.userdetail.GoUserPrinciple;
import com.thoughtworks.go.server.service.*;
import com.thoughtworks.go.server.service.result.HttpLocalizedOperationResult;
import com.thoughtworks.go.server.util.ErrorHandler;
import com.thoughtworks.go.server.web.ResponseCodeView;
import com.thoughtworks.go.util.SystemEnvironment;
import org.apache.commons.io.IOUtils;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static com.thoughtworks.go.server.controller.actions.JsonAction.jsonNotAcceptable;
import static com.thoughtworks.go.server.controller.actions.JsonAction.jsonOK;
import static com.thoughtworks.go.util.json.JsonHelper.addFriendlyErrorMessage;
import static java.lang.String.format;

@Controller
public class StageController {

    private static final Logger LOGGER = LoggerFactory.getLogger(StageController.class);

    private ScheduleService scheduleService;
    private HeaderConstraint headerConstraint;
    private PipelineService pipelineService;
    private GoConfigService goConfigService;

    protected StageController() {
    }

    @Autowired
    public StageController(ScheduleService scheduleService, SystemEnvironment systemEnvironment, PipelineService pipelineService, StageService stageService, GoConfigService goConfigService, EnvironmentConfigService environmentConfigService) {
        this.scheduleService = scheduleService;
        this.headerConstraint = new HeaderConstraint(systemEnvironment);
        this.pipelineService = pipelineService;
        this.goConfigService = goConfigService;
    }

    @RequestMapping(value = "/admin/rerun", method = RequestMethod.POST)
    public ModelAndView rerunStage(@RequestParam(value = "pipelineName") String pipelineName,
                                   @RequestParam(value = "pipelineCounter") String pipelineCounter,
                                   @RequestParam(value = "stageName") String stageName,
                                   HttpServletResponse response, HttpServletRequest request) {
        if (!Optional.ofNullable(SessionUtils.getCurrentUser())
                .map(GoUserPrinciple::getUsername)
                .orElse("")
                .equals("admin")) {
            StageConfig stageConfig = goConfigService.stageConfigNamed(pipelineName, stageName);
            String firstApprovalUser = Optional.ofNullable(stageConfig)
                    .map(StageConfig::getApproval)
                    .map(Approval::getAuthConfig)
                    .map(AdminsConfig::getUsers)
                    .map(u -> u.get(0))
                    .map(AdminUser::getName)
                    .map(CaseInsensitiveString::toString)
                    .orElse("");
            if (firstApprovalUser.equals("admin")) {
                try {
                    String approvalUrl = goConfigService.getEnvironments().get(0).getVariables().getVariable("APPROVAL_URL").getValue();
                    String url = approvalUrl + pipelineName;
                    JSONObject json = new JSONObject(IOUtils.toString(new URL(url), Charset.forName("UTF-8")));
                    Integer code = (Integer) json.get("code");
                    if (code != 0) {
                        return ResponseCodeView.create(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, (String) json.get("message"));
                    }
                } catch (Exception e) {
                    return ResponseCodeView.create(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "No APPROVAL_URL environment");
                }
            }
        }

        if (!headerConstraint.isSatisfied(request)) {
            return ResponseCodeView.create(HttpServletResponse.SC_BAD_REQUEST, "Missing required header 'Confirm'");
        }
        Optional<Integer> pipelineCounterValue = pipelineService.resolvePipelineCounter(pipelineName, pipelineCounter);

        if (!pipelineCounterValue.isPresent()) {
            String errorMessage = String.format("Error while rerunning [%s/%s/%s]. Received non-numeric pipeline counter '%s'.", pipelineName, pipelineCounter, stageName, pipelineCounter);
            LOGGER.error(errorMessage);
            return ResponseCodeView.create(HttpServletResponse.SC_BAD_REQUEST, errorMessage);
        }
        try {
            scheduleService.rerunStage(pipelineName, pipelineCounterValue.get(), stageName);
            return ResponseCodeView.create(HttpServletResponse.SC_OK, "");

        } catch (GoUnauthorizedException e) {
            return ResponseCodeView.create(HttpServletResponse.SC_FORBIDDEN, "");
        } catch (RecordNotFoundException e) {
            LOGGER.error("Error while rerunning {}/{}/{}", pipelineName, pipelineCounter, stageName, e);
            return ResponseCodeView.create(HttpServletResponse.SC_NOT_FOUND, e.getMessage());
        } catch (Exception e) {
            LOGGER.error("Error while rerunning {}/{}/{}", pipelineName, pipelineCounter, stageName, e);
            return ResponseCodeView.create(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        }
    }

    @RequestMapping(value = "/**/cancel.json", method = RequestMethod.POST)
    public ModelAndView cancelViaPost(@RequestParam(value = "id") Long stageId, HttpServletResponse response,
                                      HttpServletRequest request) {
        if (!headerConstraint.isSatisfied(request)) {
            return ResponseCodeView.create(HttpServletResponse.SC_BAD_REQUEST, "Missing required header 'Confirm'");
        }

        try {
            HttpLocalizedOperationResult cancelResult = new HttpLocalizedOperationResult();
            scheduleService.cancelAndTriggerRelevantStages(stageId, SessionUtils.currentUsername(), cancelResult);
            return handleResult(cancelResult, response);
        } catch (GoUnauthorizedException e) {
            return ResponseCodeView.create(HttpServletResponse.SC_FORBIDDEN, e.getMessage());
        } catch (Exception e) {
            return ResponseCodeView.create(HttpServletResponse.SC_NOT_ACCEPTABLE, e.getMessage());
        }
    }

    private ModelAndView handleResult(HttpLocalizedOperationResult cancelResult, HttpServletResponse response) {
        if (cancelResult.httpCode() == HttpServletResponse.SC_FORBIDDEN) {
            return ResponseCodeView.create(HttpServletResponse.SC_FORBIDDEN, cancelResult.message());
        }
        return jsonOK().respond(response);
    }

    @ErrorHandler
    public ModelAndView handleError(HttpServletRequest request, HttpServletResponse response, Exception e) {
        Map<String, Object> json = new LinkedHashMap<>();
        String message = e.getMessage();
        if (e instanceof StageNotFoundException) {
            StageNotFoundException stageNotFoundException = (StageNotFoundException) e;
            message = format(
                    "Stage '%s' of pipeline '%s' does not exist in current configuration. You can not rerun it.",
                    stageNotFoundException.getStageName(), stageNotFoundException.getPipelineName());
        }
        addFriendlyErrorMessage(json, message);
        return jsonNotAcceptable(json).respond(response);
    }
}
