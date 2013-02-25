/*
 * Copyright 2012-2013 Continuuity,Inc. All Rights Reserved.
 */

package com.continuuity.passport.http.handlers;

import com.continuuity.passport.core.exceptions.AccountAlreadyExistsException;
import com.continuuity.passport.core.exceptions.AccountNotFoundException;
import com.continuuity.passport.core.exceptions.VPCNotFoundException;
import com.continuuity.passport.core.security.UsernamePasswordApiKeyToken;
import com.continuuity.passport.core.service.AuthenticatorService;
import com.continuuity.passport.core.service.DataManagementService;
import com.continuuity.passport.core.status.AuthenticationStatus;
import com.continuuity.passport.core.utils.PasswordUtils;
import com.continuuity.passport.meta.Account;
import com.continuuity.passport.meta.VPC;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import javax.ws.rs.*;
import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Annotations for endpoints, method types and data types for handling Http requests
 * Note: Jersey has a limitation of not allowing multiple resource handlers share the same path.
 * As a result we are needing to have all the code in a single file. This will be potentially
 * huge. Need to find a work-around.
 */


@Path("/passport/v1/account/")
@Singleton
public class AccountHandler extends PassportHandler {

  private final DataManagementService dataManagementService;
  private final AuthenticatorService authenticatorService;


  @Inject
  public AccountHandler(DataManagementService dataManagementService, AuthenticatorService authenticatorService) {
    this.dataManagementService = dataManagementService;
    this.authenticatorService = authenticatorService;
  }

  @Path("{id}")
  @GET
  @Produces("application/json")
  public Response getAccountInfo(@PathParam("id") int id) {

    requestReceived();

    Account account = dataManagementService.getAccount(id);
    if (account != null) {
      requestSuccess();
      return Response.ok(account.toString()).build();
    } else {
      requestFailed();
      return Response.status(Response.Status.NOT_FOUND)
        .entity(Utils.getJsonError("Account not found"))
        .build();
    }
  }


  @Path("{id}/password")
  @PUT
  @Produces("application/json")
  @Consumes("application/json")
  public Response changePassword(@PathParam("id") int id, String data) {

    try {
      requestReceived();

      JsonParser parser = new JsonParser();
      JsonElement element = parser.parse(data);
      JsonObject jsonObject = element.getAsJsonObject();

      String oldPassword = jsonObject.get("old_password") == null ? null : jsonObject.get("old_password").getAsString();
      String newPassword = jsonObject.get("new_password") == null ? null : jsonObject.get("new_password").getAsString();

      if ((oldPassword == null) || (oldPassword.isEmpty()) ||
        (newPassword == null) || (newPassword.isEmpty())) {
        requestFailed(); // Request failed
        return Response.status(Response.Status.BAD_REQUEST)
          .entity(Utils.getJson("FAILED", "Must pass in old_password and new_password"))
          .build();
      }

      dataManagementService.changePassword(id, oldPassword, newPassword);
      //Contract for the api is to return updated account to avoid a second call from the caller to get the
      // updated account
      Account account = dataManagementService.getAccount(id);
      if (account != null) {
        requestSuccess();
        return Response.ok(account.toString()).build();
      } else {
        requestFailed(); // Request failed
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(Utils.getJson("FAILED", "Failed to get updated account"))
          .build();
      }
    } catch (Exception e) {
      requestFailed(); // Request failed
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
        .entity(Utils.getJson("FAILED", String.format("Download confirmation failed. %s", e.getMessage())))
        .build();
    }
  }

  @Path("{id}/downloaded")
  @PUT
  @Produces("application/json")
  public Response confirmDownload(@PathParam("id") int id) {
    requestReceived();

    try {

      dataManagementService.confirmDownload(id);
      //Contract for the api is to return updated account to avoid a second call from the caller to get the
      // updated account
      Account account = dataManagementService.getAccount(id);
      if (account != null) {
        requestSuccess();
        return Response.ok(account.toString()).build();
      } else {
        requestFailed(); // Request failed
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(Utils.getJson("FAILED", "Failed to get updated account"))
          .build();
      }
    } catch (Exception e) {
      requestFailed(); // Request failed
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
        .entity(Utils.getJson("FAILED", String.format("Download confirmation failed. %s", e.getMessage())))
        .build();
    }
  }

  @Path("{id}")
  @PUT
  @Produces("application/json")
  @Consumes("application/json")
  public Response updateAccount(@PathParam("id") int id, String data) {
    requestReceived();

   try {
      JsonParser parser = new JsonParser();
      JsonElement element = parser.parse(data);
      JsonObject jsonObject = element.getAsJsonObject();

      Map<String, Object> updateParams = new HashMap<String, Object>();

      String firstName = jsonObject.get("first_name") == null ? null : jsonObject.get("first_name").getAsString();
      String lastName = jsonObject.get("last_name") == null ? null : jsonObject.get("last_name").getAsString();
      String company = jsonObject.get("company") == null ? null : jsonObject.get("company").getAsString();

      //TODO: Find a better way to update the map
      if (firstName != null) {
        updateParams.put("first_name", firstName);
      }

      if (lastName != null) {
        updateParams.put("last_name", lastName);
      }

      if (company != null) {
        updateParams.put("company", company);
      }

      dataManagementService.updateAccount(id, updateParams);
      //Contract for the api is to return updated account to avoid a second call from the caller to get the
      // updated account
      Account account = dataManagementService.getAccount(id);
      if (account != null) {
        requestSuccess();
        return Response.ok(account.toString()).build();
      } else {
        requestFailed(); // Request failed
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(Utils.getJson("FAILED", "Failed to get updated account"))
          .build();
      }
    } catch (JsonParseException e) {
      requestFailed();
      return Response.status(Response.Status.BAD_REQUEST)
        .entity(Utils.getJson("FAILED", String.format("Json parse exception. %s", e.getMessage())))
        .build();
    } catch (Exception e) {
      requestFailed(); // Request failed
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
        .entity(Utils.getJson("FAILED", String.format("Account Update Failed. %s", e.getMessage())))
        .build();
      }
  }

  @POST
  @Produces("application/json")
  @Consumes("application/json")
  public Response createAccount(String data) {
    requestReceived();

    String emailId = null;
    try {
      JsonParser parser = new JsonParser();
      JsonElement element = parser.parse(data);
      JsonObject jsonObject = element.getAsJsonObject();

      emailId = jsonObject.get("email_id") == null ? null : jsonObject.get("email_id").getAsString();

      if ((emailId == null)) {
        return Response.status(Response.Status.BAD_REQUEST)
          .entity(Utils.getJson("FAILED", "Email id is missing")).build();
      } else {
        Account account = dataManagementService.registerAccount(new Account("", "", "", emailId));
        requestSuccess();
        return Response.ok(account.toString()).build();
      }
    } catch (AccountAlreadyExistsException e) {
      //If the account already exists - return the existing account so that the caller can take appropriate action
      Account account = dataManagementService.getAccount(emailId);
      requestFailed(); // Request failed
      return Response.status(Response.Status.CONFLICT)
        .entity(Utils.getJsonError("FAILED", account.toString()))
        .build();
    } catch (JsonParseException e) {
      requestFailed();
      return Response.status(Response.Status.BAD_REQUEST)
        .entity(Utils.getJson("FAILED", String.format("Json parse exception. %s", e.getMessage())))
        .build();
    }catch (Exception e) {
      requestFailed(); // Request failed
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
        .entity(Utils.getJson("FAILED", String.format("Account Creation Failed. %s", e)))
        .build();
    }
  }

  @Path("{id}/confirmed")
  @PUT
  @Produces("application/json")
  @Consumes("application/json")
  public Response confirmAccount(String data, @PathParam("id") int id) {
    requestReceived();

    try {
      JsonParser parser = new JsonParser();
      JsonElement element = parser.parse(data);
      JsonObject jsonObject = element.getAsJsonObject();

      String accountPassword = jsonObject.get("password") == null ? null : jsonObject.get("password").getAsString();
      String firstName = jsonObject.get("first_name") == null ? null : jsonObject.get("first_name").getAsString();
      String lastName = jsonObject.get("last_name") == null ? null : jsonObject.get("last_name").getAsString();
      String company = jsonObject.get("company") == null ? null : jsonObject.get("company").getAsString();


      if ((accountPassword == null) || (accountPassword.isEmpty()) ||
        (firstName == null) || (firstName.isEmpty()) ||
        (lastName == null) || (lastName.isEmpty()) ||
        (company == null) || (company.isEmpty())) {
        requestFailed(); // Request failed
        return Response.status(Response.Status.BAD_REQUEST)
          .entity(Utils.getJson("FAILED", "password, first_name, last_name, company should be passed in")).build();
      } else {
        Account account = new Account(firstName, lastName, company, id);
        dataManagementService.confirmRegistration(account, accountPassword);
        //Contract for the api is to return updated account to avoid a second call from the caller to get the
        // updated account
        Account accountFetched = dataManagementService.getAccount(id);
        if (accountFetched != null) {
          requestSuccess();
          return Response.ok(accountFetched.toString()).build();
        } else {
          requestFailed(); // Request failed
          return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
            .entity(Utils.getJson("FAILED", "Failed to get updated account"))
            .build();
        }
      }
    } catch (JsonParseException e) {
      requestFailed();
      return Response.status(Response.Status.BAD_REQUEST)
        .entity(Utils.getJson("FAILED", String.format("Json parse exception. %s", e.getMessage())))
        .build();
    } catch (Exception e) {
      requestFailed(); // Request failed
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
        .entity(Utils.getJson("FAILED", String.format("Account Confirmation Failed. %s", e)))
        .build();
    }
  }


  @Path("{id}/vpc")
  @POST
  @Produces("application/json")
  @Consumes("application/json")
  public Response createVPC(String data, @PathParam("id") int id) {
    requestReceived();

    try {
      JsonParser parser = new JsonParser();
      JsonElement element = parser.parse(data);
      JsonObject jsonObject = element.getAsJsonObject();

      String vpcName = jsonObject.get("vpc_name") == null ? null : jsonObject.get("vpc_name").getAsString();
      String vpcLabel = jsonObject.get("vpc_label") == null ? null : jsonObject.get("vpc_label").getAsString();

      if ((vpcName != null) && (!vpcName.isEmpty()) && (vpcLabel != null) && (!vpcLabel.isEmpty())) {
        VPC vpc = dataManagementService.addVPC(id, new VPC(vpcName, vpcLabel));
        requestSuccess();
        return Response.ok(vpc.toString()).build();
      } else {
        requestFailed(); // Request failed
        return Response.status(Response.Status.BAD_REQUEST)
          .entity(Utils.getJson("FAILED", "VPC creation failed. vpc_name is missing"))
          .build();
      }
    } catch (JsonParseException e) {
      requestFailed();
      return Response.status(Response.Status.BAD_REQUEST)
        .entity(Utils.getJson("FAILED", String.format("Json parse exception. %s", e.getMessage())))
        .build();
    } catch (Exception e) {
      requestFailed(); // Request failed
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
        .entity(Utils.getJson("FAILED", String.format("VPC Creation Failed. %s", e)))
        .build();

    }
  }

  @Path("{id}/vpc")
  @GET
  @Produces("application/json")
  public Response getVPC(@PathParam("id") int id) {
    requestReceived();

    try {
      List<VPC> vpcList = dataManagementService.getVPC(id);
      if (vpcList.isEmpty()) {
        return Response.ok("[]").build();
      } else {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        boolean first = true;
        for (VPC vpc : vpcList) {
          if (first) {
            first = false;
          } else {
            sb.append(",");
          }
          sb.append(vpc.toString());
        }
        sb.append("]");
        requestSuccess();
        return Response.ok(sb.toString()).build();
      }
    } catch (Exception e) {
      requestFailed(); // Request failed
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
        .entity(Utils.getJsonError(String.format("VPC get Failed. %s", e.getMessage())))
        .build();
    }
  }

  @Path("{accountId}/vpc/{vpcId}")
  @GET
  @Produces("application/json")
  public Response getSingleVPC(@PathParam("accountId") int accountId, @PathParam("vpcId") int vpcId) {
    requestReceived();

    try {
      VPC vpc = dataManagementService.getVPC(accountId, vpcId);
      if (vpc == null) {
        requestFailed(); // Request failed
        return Response.status(Response.Status.NOT_FOUND)
          .entity(Utils.getJsonError("VPC not found")).build();

      } else {
        requestSuccess();
        return Response.ok(vpc.toString()).build();
      }
    } catch (Exception e) {
      requestFailed(); // Request failed
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
        .entity(Utils.getJsonError(String.format("VPC get Failed. %s", e.getMessage())))
        .build();
    }
  }

  @Path("authenticate")
  @POST
  @Produces("application/json")
  @Consumes("application/json")
  public Response authenticate(String data, @HeaderParam("X-Continuuity-ApiKey") String apiKey) {

    //Logic -
    //  Either use emailId and password if present for auth
    //  if not present use ApiKey
    // If username and password is passed it can't be null
    // Dummy username and password is used if apiKey is passed to enable it to work with shiro

    requestReceived();
    String emailId = UsernamePasswordApiKeyToken.DUMMY_USER;
    String password = UsernamePasswordApiKeyToken.DUMMY_PASSWORD;
    boolean useApiKey = true;

    if (data != null && !data.isEmpty()) {
      JsonParser parser = new JsonParser();
      JsonElement element = parser.parse(data);
      JsonObject jsonObject = element.getAsJsonObject();

      password = jsonObject.get("password") == null ? null
        : jsonObject.get("password").getAsString();
      emailId = jsonObject.get("email_id") == null ? null
        : jsonObject.get("email_id").getAsString();
      useApiKey = false;
    }

    if (emailId == null || emailId.isEmpty() || password == null || password.isEmpty()) {
      requestFailed();
      return Response.status(Response.Status.BAD_REQUEST).entity(
        Utils.getAuthenticatedJson("Bad Request.", "Username and password can't be null"))
        .build();
    }

    UsernamePasswordApiKeyToken token = null;
    if (useApiKey) {
      token = new UsernamePasswordApiKeyToken(UsernamePasswordApiKeyToken.DUMMY_USER,
        UsernamePasswordApiKeyToken.DUMMY_PASSWORD,
        apiKey, true);
    } else {
      String hashed = PasswordUtils.generateHashedPassword(password);
      token = new UsernamePasswordApiKeyToken(emailId,
        hashed, apiKey, false);
    }

    try {

      AuthenticationStatus status = authenticatorService.authenticate(token);
      if (status.getType().equals(AuthenticationStatus.Type.AUTHENTICATED)) {
        //TODO: Better naming for authenticatedJson?
        requestSuccess();
        return Response.ok(status.getMessage()).build();
      } else {
        requestFailed(); //Failed request
        return Response.status(Response.Status.UNAUTHORIZED).entity(
          Utils.getAuthenticatedJson("Authentication Failed.", "Either user doesn't exist or password doesn't match"))
          .build();
      }
    } catch (Exception e) {
      requestFailed(); //Failed request
      return Response.status(Response.Status.UNAUTHORIZED).entity(
        Utils.getAuthenticatedJson("Authentication Failed.", e.getMessage())).build();
    }
  }

  @Path("{id}/regenerateApiKey")
  @GET
  @Produces("application/json")
  public Response regenerateApiKey(@PathParam("id") int accountId) {
    try {
      dataManagementService.regenerateApiKey(accountId);
      //Contract for the api is to return updated account to avoid a second call from the caller to get the
      // updated account
      Account accountFetched = dataManagementService.getAccount(accountId);
      if (accountFetched != null) {
        requestSuccess();
        return Response.ok(accountFetched.toString()).build();
      } else {
        requestFailed(); // Request failed
        return Response.status(Response.Status.NOT_FOUND)
          .entity(Utils.getJson("FAILED", "Failed to get regenerate key. Account not found"))
          .build();
      }
    } catch (Exception e) {
      requestFailed(); // Request failed
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
        .entity(Utils.getJson("FAILED", "Failed to get regenerate key"))
        .build();
    }
  }

  @Path("{id}")
  @DELETE
  @Produces("application/json")
  public Response deleteAccount(@PathParam("id") int id) {
    requestReceived();

    try {
      dataManagementService.deleteAccount(id);
      requestSuccess();
      return Response.ok().entity(Utils.getJsonOK()).build();
    } catch (AccountNotFoundException e) {
      requestFailed(); //Failed request
      return Response.status(Response.Status.NOT_FOUND)
        .entity(Utils.getJsonError("Account not found"))
        .build();
    } catch (RuntimeException e) {
      requestFailed(); //Failed request
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
        .entity(Utils.getJsonError("Account delete Failed", e.getMessage()))
        .build();

    }
  }

  @Path("{accountId}/vpc/{vpcId}")
  @DELETE
  @Produces("application/json")
  public Response deleteVPC(@PathParam("accountId") int accountId, @PathParam("vpcId") int vpcId) {
    requestReceived();

    try {
      dataManagementService.deleteVPC(accountId, vpcId);
      requestSuccess();
      return Response.ok().entity(Utils.getJsonOK()).build();
    } catch (VPCNotFoundException e) {
      requestFailed(); //Failed request
      return Response.status(Response.Status.NOT_FOUND)
        .entity(Utils.getJsonError("VPC not found"))
        .build();
    } catch (RuntimeException e) {
      requestFailed(); //Failed request
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
        .entity(Utils.getJsonError("VPC delete Failed", e.getMessage()))
        .build();

    }
  }

}
