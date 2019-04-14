package com.flaregames.slack.actions;

import org.apache.commons.lang.StringUtils;

import com.atlassian.confluence.core.ConfluenceActionSupport;
import com.atlassian.confluence.security.PermissionManager;
import com.atlassian.xwork.RequireSecurityToken;
import com.flaregames.slack.components.ConfigurationManager;
import com.opensymphony.xwork.Action;

public class SaveConfigurationAction extends ConfluenceActionSupport {
   private static final long    serialVersionUID = 1704624386670934630L;

   private ConfigurationManager configurationManager;
   private PermissionManager    permissionManager;

   private String               spaceKey;
   private String               slackWebhookUrl;
   private String                slackUser;

   @Override
   public boolean isPermitted() {
      return permissionManager.isConfluenceAdministrator(getAuthenticatedUser());
   }

   public void setSlackWebhookUrl(String slackWebhookUrl) {
      this.slackWebhookUrl = slackWebhookUrl;
   }

   public void setSlackUser(String slackUser) {
      System.out.println(("setSlack User calleD!" + slackUser));
      this.slackUser = slackUser;
   }
   @Override
   public void validate() {
      if (StringUtils.isBlank(slackWebhookUrl)) {
         addActionError(getText("slack.webhookurl.form.invalid"));
      }
   }

   @Override
   @RequireSecurityToken(true)
   public String execute() throws Exception {
      configurationManager.setWebhookUrl(slackWebhookUrl);
      configurationManager.setSlackUser(slackUser);
      if (StringUtils.isNotBlank(spaceKey)) {
         return "redirect";
      }
      return Action.SUCCESS;
   }

   public String getSpaceKey() {
      return spaceKey;
   }

   public void setSpaceKey(String spaceKey) {
      this.spaceKey = spaceKey;
   }

   // =================================================================================================================
   // We have to use setter injection if we don't use the defaultStack
   // See https://jira.atlassian.com/browse/CONF-23137
   public void setConfigurationManager(ConfigurationManager configurationManager) {
      this.configurationManager = configurationManager;
   }

   @Override
   public void setPermissionManager(PermissionManager permissionManager) {
      this.permissionManager = permissionManager;
   }
}