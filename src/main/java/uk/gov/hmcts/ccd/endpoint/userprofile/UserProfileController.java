package uk.gov.hmcts.ccd.endpoint.userprofile;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.google.common.collect.Lists;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.ccd.AppInsights;
import uk.gov.hmcts.ccd.domain.model.CallbackRequest;
import uk.gov.hmcts.ccd.domain.model.CallbackResponse;
import uk.gov.hmcts.ccd.domain.model.UserProfile;
import uk.gov.hmcts.ccd.domain.service.DeleteUserProfileJurisdictionOperation;
import uk.gov.hmcts.ccd.domain.service.FindAllUserProfilesOperation;
import uk.gov.hmcts.ccd.domain.service.SaveUserProfileOperation;
import uk.gov.hmcts.ccd.domain.service.UserProfileOperation;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import javax.transaction.Transactional;

@RestController
class UserProfileController {
    private static final JsonNodeFactory JSON_NODE_FACTORY = new JsonNodeFactory(false);
    private final FindAllUserProfilesOperation findAllUserProfilesOperation;
    private final UserProfileOperation userProfileOperation;
    private final SaveUserProfileOperation saveUserProfileOperation;
    private final DeleteUserProfileJurisdictionOperation deleteUserProfileJurisdictionOperation;
    private final AppInsights appInsights;

    @Autowired
    public UserProfileController(FindAllUserProfilesOperation findAllUserProfilesOperation,
                                 UserProfileOperation userProfileOperation,
                                 SaveUserProfileOperation saveUserProfileOperation,
                                 DeleteUserProfileJurisdictionOperation deleteUserProfileJurisdictionOperation,
                                 AppInsights appInsights) {
        this.findAllUserProfilesOperation = findAllUserProfilesOperation;
        this.userProfileOperation = userProfileOperation;
        this.saveUserProfileOperation = saveUserProfileOperation;
        this.deleteUserProfileJurisdictionOperation = deleteUserProfileJurisdictionOperation;
        this.appInsights = appInsights;
    }

    @RequestMapping(value = "/callback", method = RequestMethod.POST)
    @ResponseStatus(HttpStatus.OK)
    @ApiOperation(value = "Callback",
        notes = "Optional filtering of results via \"jurisdiction\" request parameter")
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Found User Profiles"),
        @ApiResponse(code = 400, message = "Unable to find User Profiles")
    })
    public CallbackResponse postCallback(@RequestBody final CallbackRequest request) {
        Instant start = Instant.now();
        CallbackResponse callbackResponse;
        if (fieldValueMatches(request, "TextFieldFName", "John")
            || fieldValueMatches(request, "TextFieldMName", "Smith")) {
            callbackResponse = new CallbackResponse();
            callbackResponse.setErrors(Lists.newArrayList("You've entered invalid data"));
        } else {
            callbackResponse = new CallbackResponse();
            callbackResponse.setData(request.getCaseDetails().getData());
            callbackResponse.setDataClassification(request.getCaseDetails().getDataClassification());
            callbackResponse.setSecurityClassification(request.getCaseDetails().getSecurityClassification());
        }
        final Duration between = Duration.between(start, Instant.now());
        appInsights.trackRequest(between, true);
        return callbackResponse;
    }

    private boolean fieldValueMatches(@RequestBody final CallbackRequest request, String fieldName, String fieldValue) {
        return request.getCaseDetails().getData().containsKey(fieldName)
        && request.getCaseDetails().getData().get(fieldName).equals(JSON_NODE_FACTORY.textNode(fieldValue));
    }

    @RequestMapping(value = "/users", method = RequestMethod.GET)
    @ResponseStatus(HttpStatus.OK)
    @ApiOperation(value = "Get User Profiles",
                  notes = "Optional filtering of results via \"jurisdiction\" request parameter")
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Found User Profiles"),
        @ApiResponse(code = 400, message = "Unable to find User Profiles")
    })
    public List<UserProfile> getUserProfiles(@ApiParam("Jurisdiction ID")
                                             @RequestParam("jurisdiction")
                                                 final Optional<String> jurisdictionOptional,
                                             @RequestHeader(value = "actionedBy", defaultValue = "<UNKNOWN>")
                                                 final String actionedBy) {
        Instant start = Instant.now();
        List<UserProfile> userProfiles = jurisdictionOptional
            .map(j -> findAllUserProfilesOperation.getAll(j, actionedBy))
            .orElse(findAllUserProfilesOperation.getAll());
        final Duration between = Duration.between(start, Instant.now());
        appInsights.trackRequest(between, true);
        return userProfiles;
    }

    @Transactional
    @RequestMapping(value = "/users", method = RequestMethod.PUT)
    @ResponseStatus(HttpStatus.OK)
    @ApiOperation(value = "Update a new User Profile",
                  notes = "A User Profile or Jurisdiction is created if it does not exist")
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Updated User Profile defaults")
    })
    public void populateUserProfiles(@RequestBody final List<UserProfile> userProfiles,
                                     @RequestHeader(value = "actionedBy", defaultValue = "<UNKNOWN>")
                                     final String actionedBy) {
        userProfileOperation.execute(userProfiles, actionedBy);
    }

    @Transactional
    @RequestMapping(value = "/users/save", method = RequestMethod.PUT)
    @ResponseStatus(HttpStatus.OK)
    @ApiOperation(value = "Save a User Profile",
                  notes = "A User Profile and/or Jurisdiction is created if it does not exist. Behaves exactly the "
                    + "same as the `PUT` /users endpoint, except that an HTTP 400 Bad Request is returned if the user "
                    + "already belongs to the given Jurisdiction")
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Saved User Profile"),
        @ApiResponse(code = 400, message = "User Profile does not exist, or user already belongs to given Jurisdiction")
    })
    public UserProfile saveUserProfile(@RequestBody final UserProfile userProfile,
                                       @RequestHeader(value = "actionedBy", defaultValue = "<UNKNOWN>")
                                       final String actionedBy) {
        return saveUserProfileOperation.saveUserProfile(userProfile, actionedBy);
    }

    @Transactional
    @RequestMapping(value = "/users", method = RequestMethod.DELETE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @ApiOperation(value = "Delete the association to a Jurisdiction from a User Profile",
                  notes = "Deletes the Jurisdiction unless it matches the user's Workbasket default Jurisdiction AND "
                    + "the user currently belongs to more than one Jurisdiction. If the Jurisdiction being deleted "
                    + "is the user's sole Jurisdiction, removal is permitted. In addition, their Workbasket defaults "
                    + "are set to null, since they no longer belong to any Jurisdictions")
    @ApiResponses(value = {
        @ApiResponse(code = 204, message = "Deleted Jurisdiction from User Profile"),
        @ApiResponse(code = 400, message = "User Profile does not exist, user is not a member of specified "
                                    + "Jurisdiction, or user's Workbasket defaults are for the Jurisdiction being "
                                    + "removed")
    })
    public void deleteJurisdictionFromUserProfile(@ApiParam("User Profile ID")
                                                  @RequestParam("uid")
                                                  final String uid,
                                                  @ApiParam("Jurisdiction ID")
                                                  @RequestParam("jid")
                                                  final String jid,
                                                  @RequestHeader(value = "actionedBy", defaultValue = "<UNKNOWN>")
                                                      final String actionedBy) {
        deleteUserProfileJurisdictionOperation.deleteAssociation(uid, jid, actionedBy);
    }
}
