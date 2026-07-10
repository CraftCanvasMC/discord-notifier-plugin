package nz.co.jammehcow.jenkinsdiscord;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.Plugin;
import hudson.PluginWrapper;
import hudson.matrix.MatrixConfiguration;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;
import java.io.IOException;
import java.time.Instant;
import java.util.List;

import jenkins.model.Jenkins;
import jenkins.model.JenkinsLocationConfiguration;
import nz.co.jammehcow.jenkinsdiscord.exception.WebhookException;
import nz.co.jammehcow.jenkinsdiscord.util.EmbedDescription;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

/**
 * Author: jammehcow.
 * Date: 22/04/17.
 */

@SuppressFBWarnings(value = "NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE", justification = "Requires triage")
public class WebhookPublisher extends Notifier {
    private final String webhookURL;
    private final String branchName;
    private final String statusTitle;
    private final String thumbnailURL;
    private final String successRole;
    private final String customAvatarUrl;
    private final String customUsername;
    private DynamicFieldContainer dynamicFieldContainer;
    private final boolean sendOnStateChange;
    private final boolean sendOnlyFailed;
    private boolean enableUrlLinking;
    private final boolean enableFooterInfo;
    private boolean showChangeset;
    private boolean sendLogFile;
    private boolean sendStartNotification;
    private static final String NAME = "Discord Notifier";
    private static final String SHORT_NAME = "discord-notifier";
    private String successColor;
    private String abortedColor;
    private String failureColor;

    @DataBoundConstructor
    public WebhookPublisher(
        String webhookURL,
        String thumbnailURL,
        boolean sendOnStateChange,
        String statusTitle,
        String successRole,
        String branchName,
        String customAvatarUrl,
        String customUsername,
        boolean sendOnStateFailed,
        boolean sendOnlyFailed,
        boolean enableUrlLinking,
        boolean enableArtifactList,
        boolean enableFooterInfo,
        boolean showChangeset,
        boolean sendLogFile,
        boolean sendStartNotification,
        String scmWebUrl
    ) {
        this.webhookURL = webhookURL;
        this.thumbnailURL = thumbnailURL;
        this.sendOnStateChange = sendOnStateChange;
        this.sendOnlyFailed = sendOnlyFailed;
        this.enableUrlLinking = enableUrlLinking;
        this.enableFooterInfo = enableFooterInfo;
        this.showChangeset = showChangeset;
        this.branchName = branchName;
        this.statusTitle = statusTitle;
        this.successRole = successRole;
        this.customAvatarUrl = customAvatarUrl;
        this.customUsername = customUsername;
        this.sendLogFile = sendLogFile;
        this.sendStartNotification = sendStartNotification;
    }

    public String getWebhookURL() {
        return this.webhookURL;
    }

    public String getBranchName() {
        return this.branchName;
    }

    public String getStatusTitle() {
        return this.statusTitle;
    }

    public String getCustomAvatarUrl() {
        return this.customAvatarUrl;
    }

    public String getCustomUsername() {
        return this.customUsername;
    }

    @DataBoundSetter
    public void setDynamicFieldContainer(String fieldsString) {
        this.dynamicFieldContainer = DynamicFieldContainer.of(fieldsString);
    }

    public String getDynamicFieldContainer() {
        if(dynamicFieldContainer == null){
            return "";
        }
        return dynamicFieldContainer.toString();
    }

    public String getSuccessRole() {
        return this.successRole;
    }

    public String getThumbnailURL() {
        return this.thumbnailURL;
    }

    public boolean isSendOnStateChange() {
        return this.sendOnStateChange;
    }

    public boolean isSendOnlyFailed() {
        return this.sendOnlyFailed;
    }


    public boolean isEnableUrlLinking() {
        return this.enableUrlLinking;
    }

    public boolean isEnableArtifactList() {
        return false;
    }

    public boolean isEnableFooterInfo() {
        return this.enableFooterInfo;
    }

    public boolean isShowChangeset() {
        return this.showChangeset;
    }

    public boolean isSendLogFile() {
        return this.sendLogFile;
    }

    public boolean isSendStartNotification() {
        return this.sendStartNotification;
    }

    public String getSuccessColor() {
        return this.successColor;
    }

    @DataBoundSetter
    public void setSuccessColor(String successColor) {
        this.successColor = successColor;
    }

    public String getAbortedColor() {
        return this.abortedColor;
    }

    @DataBoundSetter
    public void setAbortedColor(String abortedColor) {
        this.abortedColor = abortedColor;
    }

    public String getFailureColor() {
        return this.failureColor;
    }

    @DataBoundSetter
    public void setFailureColor(String failureColor) {
        this.failureColor = failureColor;
    }

    @Override
    public boolean needsToRunAfterFinalized() {
        return true;
    }

    @Override
    public boolean prebuild(AbstractBuild<?, ?> build, BuildListener listener) {
        return true;
    }

    //TODO clean this function
    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
        final EnvVars env = build.getEnvironment(listener);
        // The global configuration, used to fetch the instance url
        JenkinsLocationConfiguration globalConfig = JenkinsLocationConfiguration.get();
        if (build.getResult() == null) {
            listener.getLogger().println("[Discord Notifier] build.getResult() is null!");
            return true;
        }

        if (build.getResult().equals(hudson.model.Result.ABORTED)) {
            // no sendy for abort
            return true;
        }

        // Create a new webhook payload
        DiscordWebhook wh = new DiscordWebhook(env.expand(this.webhookURL));

        if (this.webhookURL.isEmpty()) {
            // Stop the plugin from continuing when the webhook URL isn't set. Shouldn't happen due to form validation
            listener.getLogger().println("The Discord webhook is not set!");
            return true;
        }

        if (this.enableUrlLinking && (globalConfig.getUrl() == null || globalConfig.getUrl().isEmpty())) {
            // Disable linking when the instance URL isn't set
            listener.getLogger().println("Your Jenkins URL is not set (or is set to localhost)! Disabling linking.");
            this.enableUrlLinking = false;
        }

        if (this.sendOnStateChange) {
            if (build.getPreviousBuild() != null && build.getResult().equals(build.getPreviousBuild().getResult())) {
                // Stops the webhook payload being created if the status is the same as the previous
                return true;
            }
        }

        if (this.sendOnlyFailed) {
            if (!build.getResult().equals(Result.FAILURE)) {
                return true;
            }
        }

        if (this.sendLogFile) {
            wh.setFile(build.getLogInputStream(), "build" + build.getNumber() + ".log");
        }

        Result buildresult = build.getResult();
        if (!buildresult.isCompleteBuild()) return true;

        AbstractProject project = build.getProject();

        String branchNameString = "\n";
        String resolvedBranchName = resolveBranchName(env);
        if (resolvedBranchName != null && !resolvedBranchName.isEmpty()) {
            branchNameString += "**Branch:** `" + resolvedBranchName + "`\n";
        }

        String descriptionPrefix = "## Build `" + build.getId() + "` for " + project.getDisplayName();
        long unixSeconds = Instant.now().getEpochSecond();
        descriptionPrefix += branchNameString
            + "**Published** <t:" + unixSeconds + ":f> - " + "<t:" + unixSeconds + ":R>\n";

        if (build.getResult().equals(hudson.model.Result.FAILURE)) {
            wh.setContent(env.expand("-# **BUILD FAILED <@&1179954304006750339>!**"));
        } else if (build.getResult().equals(hudson.model.Result.SUCCESS) && successRole != null && !successRole.isEmpty()) {
            wh.setContent(env.expand("-# **New build <@&" + successRole + ">!**"));
        }

        String scmWebUrl = getGitUrl(project);

        wh.setCustomAvatarUrl("https://canvasmc.io/logo_512.png");
        wh.setCustomUsername("CanvasBot");
        wh.setThumbnail(thumbnailURL == null || thumbnailURL.isBlank() ? "https://canvasmc.io/logo_512.png" : thumbnailURL);

        wh.setDescription(
            new EmbedDescription(build, globalConfig, descriptionPrefix, false, this.showChangeset, scmWebUrl, this.successRole)
                .toString()
        );

        addDynamicFieldsToWebhook(dynamicFieldContainer, wh, env);
        wh.setStatusByColor(resolveColor(listener, buildresult, successColor, abortedColor, failureColor));

        String diffUrl = resolveGithubDiffUrl(build, scmWebUrl);
        if (diffUrl != null) {
            wh.addButton("View Diff", diffUrl);
        }
        wh.addButton("Click to Download", "https://canvasmc.io/downloads/");

        try {
            listener.getLogger().println("Sending notification to Discord.");
            wh.send();
        } catch (WebhookException e) {
            e.printStackTrace(listener.getLogger());
        }

        return true;
    }

    private String getGitUrl(AbstractProject project) {
        hudson.scm.SCM scm = project.getScm();
        if (scm == null || !scm.getClass().getName().equals("hudson.plugins.git.GitSCM")) {
            return null;
        }
        try {
            Object userRemoteConfigs = scm.getClass().getMethod("getUserRemoteConfigs").invoke(scm);
            List<?> remotes = (List<?>) userRemoteConfigs;
            if (remotes.isEmpty()) return null;
            Object firstRemote = remotes.get(0);
            return (String) firstRemote.getClass().getMethod("getUrl").invoke(firstRemote);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private String resolveBranchName(EnvVars env) {
        if (branchName != null && !branchName.isEmpty()) {
            return env.expand(branchName);
        }

        String[] candidateVars = {"BRANCH_NAME", "GIT_BRANCH", "CHANGE_BRANCH", "SVN_BRANCH"};
        for (String var : candidateVars) {
            String value = env.get(var);
            if (value != null && !value.isEmpty()) {
                if ("GIT_BRANCH".equals(var) && value.contains("/")) {
                    value = value.substring(value.indexOf('/') + 1);
                }
                return value;
            }
        }

        return null;
    }

    private String resolveGithubDiffUrl(AbstractBuild build, String scmWebUrl) {
        if (scmWebUrl == null || scmWebUrl.isEmpty()) {
            return null;
        }
        try {
            hudson.scm.ChangeLogSet<? extends hudson.scm.ChangeLogSet.Entry> changeSet = build.getChangeSet();
            if (changeSet == null || changeSet.isEmptySet()) {
                return null;
            }

            Object[] entries = changeSet.getItems();
            if (entries.length == 0) {
                return null;
            }

            String base = normalizeGithubUrl(scmWebUrl);
            if (base == null) {
                return null;
            }

            String githubUrl;
            if (entries.length == 1) {
                String sha = ((hudson.scm.ChangeLogSet.Entry) entries[0]).getCommitId();
                if (sha == null) {
                    return null;
                }
                githubUrl = base + "/commit/" + sha;
            } else {
                hudson.scm.ChangeLogSet.Entry firstEntry = (hudson.scm.ChangeLogSet.Entry) entries[0];
                hudson.scm.ChangeLogSet.Entry lastEntry = (hudson.scm.ChangeLogSet.Entry) entries[entries.length - 1];

                String beforeSha = firstEntry.getCommitId();
                String afterSha = lastEntry.getCommitId();

                if (beforeSha == null || afterSha == null) {
                    return null;
                }

                githubUrl = beforeSha.equals(afterSha)
                    ? base + "/commit/" + afterSha
                    : base + "/compare/" + beforeSha + ".." + afterSha;
            }

            return toDiffsDevUrl(githubUrl);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String normalizeGithubUrl(String remoteUrl) {
        String url = remoteUrl.trim();

        if (url.startsWith("git@github.com:")) {
            url = "https://github.com/" + url.substring("git@github.com:".length());
        }

        if (!url.contains("github.com")) {
            return null;
        }

        if (url.endsWith(".git")) {
            url = url.substring(0, url.length() - 4);
        }
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }

        return url;
    }

    public static String toDiffsDevUrl(String githubUrl) {
        return "https://diffs.dev/?github_url="
            + java.net.URLEncoder.encode(githubUrl, java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * Add all key value field pairs to the webhook
     */
    private void addDynamicFieldsToWebhook(DynamicFieldContainer dynamicFieldContainer, DiscordWebhook wh, EnvVars env){
        // Early exit if we don't have any dynamicFieldContainer set
        if(dynamicFieldContainer == null){
            return;
        }
        // Go through all fields and add them to the webhook
        dynamicFieldContainer.getFields().forEach(pair -> wh.addField(pair.getKey() + ":", env.expand(pair.getValue())));
    }

    /**
     * Resolves the integer color code to use for the embed based on the build result.
     * Uses the provided custom color string when non-empty and parseable; falls back to the default.
     *
     * @param buildResult   the build result used to select the appropriate color bucket
     * @param successColor  custom hex/decimal color string for successful builds, or null/empty for default
     * @param abortedColor custom hex/decimal color string for aborted builds, or null/empty for default
     * @param failureColor  custom hex/decimal color string for failed builds, or null/empty for default
     * @return the resolved integer color code
     */
    private static int resolveColor(
        BuildListener listener,
        Result buildResult,
        String successColor,
        String abortedColor,
        String failureColor
    ) {
        String custom;
        String customFieldName;
        DiscordWebhook.StatusColor defaultColor;
        if (buildResult.isBetterOrEqualTo(Result.SUCCESS)) {
            custom = successColor;
            customFieldName = "successColor";
            defaultColor = DiscordWebhook.StatusColor.BLUE;
        } else if (buildResult.isWorseThan(Result.UNSTABLE)) {
            custom = failureColor;
            customFieldName = "failureColor";
            defaultColor = DiscordWebhook.StatusColor.RED;
        } else {
            custom = abortedColor;
            customFieldName = "abortedColor";
            defaultColor = DiscordWebhook.StatusColor.GREY;
        }
        if (custom != null && !custom.isEmpty()) {
            try {
                return DiscordWebhook.parseColor(custom);
            } catch (NumberFormatException e) {
                listener.getLogger().println(
                    "[Discord Notifier] Invalid " + customFieldName + " value '" + custom
                        + "'. Using default color."
                );
            }
        }
        return (int) defaultColor.getCode();
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        final Plugin p = Jenkins.get().getPlugin(SHORT_NAME);

        public FormValidation doCheckWebhookURL(@QueryParameter String value) {
            if (!value.matches("https://(canary\\.|ptb\\.|)discord(app)*\\.com/api/webhooks/\\d{18,19}/(\\w|-|_)*(/?)"))
                return FormValidation.error("Please enter a valid Discord webhook URL.");
            return FormValidation.ok();
        }

        @NonNull
        public String getDisplayName() {
            if (p == null) {
                return NAME;
            } else {
                return p.getWrapper().getDisplayName();
            }
        }

        public String getPluginVersion() {
            if (p == null) {
                return "";
            } else {
                return p.getWrapper().getVersion();
            }
        }
    }

    private static String getMarkdownHyperlink(String content, String url) {
        url = url.replaceAll("\\)", "\\\\\\)");
        return "[" + content + "](" + url + ")";
    }
}