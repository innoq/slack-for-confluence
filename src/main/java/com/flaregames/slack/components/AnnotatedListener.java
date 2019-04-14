package com.flaregames.slack.components;

import com.atlassian.confluence.event.events.content.ContentEvent;
import com.atlassian.confluence.event.events.content.blogpost.BlogPostCreateEvent;
import com.atlassian.confluence.event.events.content.comment.CommentCreateEvent;
import com.atlassian.confluence.event.events.content.page.PageCreateEvent;
import com.atlassian.confluence.event.events.content.page.PageUpdateEvent;
import com.atlassian.confluence.pages.AbstractPage;
import com.atlassian.confluence.pages.TinyUrl;
import com.atlassian.confluence.security.Permission;
import com.atlassian.confluence.security.PermissionManager;
import com.atlassian.confluence.user.ConfluenceUser;
import com.atlassian.confluence.user.PersonalInformationManager;
import com.atlassian.confluence.user.UserAccessor;
import com.atlassian.event.api.EventListener;
import com.atlassian.event.api.EventPublisher;
import com.atlassian.plugin.webresource.UrlMode;
import com.atlassian.plugin.webresource.WebResourceUrlProvider;
import com.atlassian.user.EntityException;
import com.atlassian.user.User;
import com.atlassian.user.search.query.UserNameTermQuery;
import in.ashwanthkumar.slack.webhook.Slack;
import in.ashwanthkumar.slack.webhook.SlackMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;

public class AnnotatedListener implements DisposableBean, InitializingBean {
   private static final Logger              LOGGER = LoggerFactory.getLogger(AnnotatedListener.class);

   private final WebResourceUrlProvider     webResourceUrlProvider;
   private final EventPublisher             eventPublisher;
   private final ConfigurationManager       configurationManager;
   private final PersonalInformationManager personalInformationManager;
   private final PermissionManager permissionManager;

   private final UserAccessor userAccessor;

   public AnnotatedListener(EventPublisher eventPublisher, ConfigurationManager configurationManager,
                            PersonalInformationManager personalInformationManager, WebResourceUrlProvider webResourceUrlProvider, PermissionManager permissionManager, UserAccessor userAccessor) {
      this.eventPublisher = checkNotNull(eventPublisher);
      this.configurationManager = checkNotNull(configurationManager);
      this.personalInformationManager = checkNotNull(personalInformationManager);
      this.webResourceUrlProvider = checkNotNull(webResourceUrlProvider);
      this.permissionManager = permissionManager;
      this.userAccessor = userAccessor;
   }

   @EventListener
   public void blogPostCreateEvent(BlogPostCreateEvent event) {
      AbstractPage page = event.getBlogPost();
      SlackMessage message = getBaseMessage(page, "new blog post");
      message.text("- Excerpt: ");
      message.text(page.getExcerpt());
      sendMessages(event, event.getBlogPost(), message);
   }

   @EventListener
   public void pageCreateEvent(PageCreateEvent event) {
      AbstractPage page = event.getPage();
      SlackMessage message = getBaseMessage(page, "new page created");
      message.text("- Excerpt: ");
      message.text(page.getExcerpt());
      sendMessages(event, page, message);
   }

   @EventListener
   public void pageUpdateEvent(PageUpdateEvent event) {
      AbstractPage page = event.getPage();
      SlackMessage message = getBaseMessage(page, "page updated");
      if (page.isVersionCommentAvailable())
         message = message.text(" - Change comment: \"" + page.getVersionComment() + "\" - ");
      String link = webResourceUrlProvider.getBaseUrl(UrlMode.ABSOLUTE)
	  + "/pages/diffpagesbyversion.action?pageId=" + page.getIdAsString()
	  + "&selectedPageVersions=" + page.getVersion()
	  + "&selectedPageVersions=" + page.getPreviousVersion();
      message = message.link(link, "View Change");
      message = message.text(". ");
      sendMessages(event, page, message);
   }

   @EventListener
   public void commentCreateEvent(CommentCreateEvent event) {
      AbstractPage page = (AbstractPage)(event.getComment().getContainer());
      SlackMessage message = getBaseMessage(page, "comment created");
      message.text(" - Comment: ");
      message.text(event.getComment().getBodyAsStringWithoutMarkup());
      sendMessages(event, page, message);

   }

   private boolean isAllowedForSlack(Object entity) {
      String username = configurationManager.getSlackUser();
      ConfluenceUser user = userAccessor.getUserByName(username);
      if (user == null ) {
         LOGGER.warn("Could not find user '{}' configured for slack-for-confluence!", username);
         return false;
      }
      return permissionManager.hasPermissionNoExemptions(user, Permission.VIEW, entity);
   }

   private void sendMessages(ContentEvent event, AbstractPage page, SlackMessage message) {
      if (event.isSuppressNotifications() || !isAllowedForSlack(page))  {
         LOGGER.info("Suppressing notification for {}.", page.getTitle());
         return;
      }
      for (String channel : getChannels(page)) {
         sendMessage(channel, message);
      }
   }

   private List<String> getChannels(AbstractPage page) {
      String spaceChannels = configurationManager.getSpaceChannels(page.getSpaceKey());
      if (spaceChannels.isEmpty()) {
         return Collections.emptyList();
      }
      return Arrays.asList(spaceChannels.split(","));
   }

   private SlackMessage getBaseMessage(AbstractPage page, String action) {
      ConfluenceUser user = page.getLastModifier() != null ? page.getLastModifier() : page.getCreator();
      SlackMessage message = new SlackMessage();
      message = appendPageLink(message, page);
      message = message.text(" - " + action + " by ");
      message = appendPersonalSpaceUrl(message, user);
      return message;
   }

   private void sendMessage(String channel, SlackMessage message) {
      LOGGER.info("Sending to {} on channel {} with message {}.", configurationManager.getWebhookUrl(), channel,
            message.toString());
      try {
         new Slack(configurationManager.getWebhookUrl()).sendToChannel(channel).push(message);
      }
      catch (IOException e) {
         LOGGER.error("Error when sending Slack message", e);
      }
   }

   private SlackMessage appendPersonalSpaceUrl(SlackMessage message, User user) {
      if (null == user) {
         return message.text("unknown user. ");
      }
      message = message.link(webResourceUrlProvider.getBaseUrl(UrlMode.ABSOLUTE) + "/"
            + personalInformationManager.getOrCreatePersonalInformation(user).getUrlPath(), user.getFullName());
      return message.text(". ");
   }

   private SlackMessage appendPageLink(SlackMessage message, AbstractPage page) {
      return message.link(tinyLink(page), page.getSpace().getDisplayTitle() + " - " + page.getTitle());
   }

   private String tinyLink(AbstractPage page) {
      return webResourceUrlProvider.getBaseUrl(UrlMode.ABSOLUTE) + "/x/" + new TinyUrl(page).getIdentifier();
   }

   @Override
   public void afterPropertiesSet() throws Exception {
      LOGGER.debug("Register Slack event listener");
      eventPublisher.register(this);
   }

   @Override
   public void destroy() throws Exception {
      LOGGER.debug("Un-register Slack event listener");
      eventPublisher.unregister(this);
   }
}
