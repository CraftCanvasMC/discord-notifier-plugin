package nz.co.jammehcow.jenkinsdiscord;

import jenkins.model.Jenkins;
import kong.unirest.HttpResponse;
import kong.unirest.JsonNode;
import kong.unirest.Proxy;
import kong.unirest.Unirest;
import kong.unirest.UnirestException;
import nz.co.jammehcow.jenkinsdiscord.exception.WebhookException;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.util.regex.Pattern;

/**
 * Author: jammehcow.
 * Date: 22/04/17.
 */
class DiscordWebhook {
    private String webhookUrl;
    private JSONObject obj;
    private JSONObject embed;
    private JSONArray fields;
    private JSONArray components;
    private InputStream file;
    private String filename;

    static final int TITLE_LIMIT = 256;
    static final int DESCRIPTION_LIMIT = 2048;
    static final int FOOTER_LIMIT = 2048;
    private static final Pattern SIX_DIGIT_HEX_COLOR = Pattern.compile("[0-9A-Fa-f]{6}");

    enum StatusColor {
        /**
         * Green "you're sweet as" color.
         */
        BLUE(1687548),
        /**
         * Yellow "go, but I'm watching you" color.
         */
        YELLOW(16776970),
        /**
         * Red "something ain't right" color.
         */
        RED(16336451),
        /**
         * Grey. Just grey.
         */
        GREY(13487565);
        private final long code;

        StatusColor(int code) {
            this.code = code;
        }

        public long getCode() {
            return code;
        }
    }

    /**
     * Parses a color string into an integer color code.
     * Accepts hex strings with or without a leading '#' (e.g. "#19BFFC" or "19BFFC"),
     * as well as plain decimal integers (e.g. "1681177").
     *
     * @param colorStr the color string to parse
     * @return the integer color code
     * @throws NumberFormatException if the string cannot be parsed as a color
     */
    static int parseColor(String colorStr) {
        String s = colorStr.trim();
        int parsed;
        try {
            if (s.startsWith("#")) {
                parsed = Integer.parseInt(s.substring(1), 16);
            } else if (SIX_DIGIT_HEX_COLOR.matcher(s).matches()) {
                parsed = Integer.parseInt(s, 16);
            } else {
                parsed = Integer.parseInt(s);
            }
        } catch (NumberFormatException e) {
            throw new NumberFormatException("Cannot parse '" + colorStr + "' as a color code");
        }

        if (parsed < 0 || parsed > 0xFFFFFF) {
            throw new NumberFormatException("Color '" + colorStr + "' is out of range (0..16777215)");
        }

        return parsed;
    }

    /**
     * Instantiates a new Discord webhook.
     *
     * @param url the webhook URL
     */
    DiscordWebhook(String url) {
        this.webhookUrl = url;
        this.obj = new JSONObject();
        this.obj.put("username", "Jenkins");
        this.obj.put("avatar_url", "https://get.jenkins.io/art/jenkins-logo/1024x1024/headshot.png");
        this.embed = new JSONObject();
        this.fields = new JSONArray();
    }

    /**
     * Sets the embed title.
     *
     * @param title the title text
     * @return this
     */
    public DiscordWebhook setTitle(String title) {
        this.embed.put("title", title);
        return this;
    }

    public DiscordWebhook setCustomUsername(String username) {
        if (username != null && !username.isEmpty())
            this.obj.put("username", username);
        else {
            // unset will allow default discord username to be used (as specified in discord's integration settings)
            this.obj.remove("username");
        }
        return this;
    }

    public DiscordWebhook setCustomAvatarUrl(String url) {
        if (url != null && !url.isEmpty())
            this.obj.put("avatar_url", url);
        else {
            // unset will allow default avatar to be used (as specified in discord's integration settings)
            this.obj.remove("avatar_url");
        }
        return this;
    }

    /**
     * Sets the embed title url.
     *
     * @param buildUrl the build url
     * @return this
     */
    public DiscordWebhook setURL(String buildUrl) {
        this.embed.put("url", buildUrl);
        return this;
    }

    /**
     * Sets the build status (for the embed's color).
     *
     * @param color the status color
     * @return this
     */
    public DiscordWebhook setStatus(StatusColor color) {
        this.embed.put("color", color.getCode());
        return this;
    }

    /**
     * Sets the embed color directly using a raw integer color code.
     *
     * @param colorCode the integer color code
     * @return this
     */
    public DiscordWebhook setStatusByColor(int colorCode) {
        this.embed.put("color", colorCode);
        return this;
    }

    /**
     * Sets the embed description.
     *
     * @param content the content
     * @return this
     */
    public DiscordWebhook setDescription(String content) {
        this.embed.put("description", content);
        return this;
    }

    public DiscordWebhook setContent(String content) {
        this.obj.put("content", content);
        return this;
    }

    /**
     * Sets the URL of image at the bottom of embed.
     *
     * @param url URL of image
     * @return this
     */
    public DiscordWebhook setImage(String url) {
        JSONObject image = new JSONObject();
        image.put("url", url);
        this.embed.put("image", image);
        return this;
    }

    /**
     * Sets the URL of image on the right side.
     *
     * @param url URL of image
     * @return this
     */
    public DiscordWebhook setThumbnail(String url) {
        JSONObject thumbnail = new JSONObject();
        thumbnail.put("url", url);
        this.embed.put("thumbnail", thumbnail);
        return this;
    }

    public DiscordWebhook addField(String name, String value) {
        JSONObject field = new JSONObject();
        field.put("name", name);
        field.put("value", value);
        this.fields.put(field);
        return this;
    }

    public DiscordWebhook addButton(String label, String url) {
        if (this.components == null) {
            this.components = new JSONArray();
        }
        JSONObject button = new JSONObject();
        button.put("type", 2);   // Button
        button.put("style", 5);  // Link style
        button.put("label", label);
        button.put("url", url);
        this.components.put(button);
        return this;
    }

    /**
     * Sets the embed's footer text.
     *
     * @param text the footer text
     * @return this
     */
    public DiscordWebhook setFooter(String text) {
        this.embed.put("footer", new JSONObject().put("text", text));
        return this;
    }

    DiscordWebhook setFile(InputStream is, String filename) {
        this.file = is;
        this.filename = filename;
        return this;
    }

    /**
     * Send the payload to Discord.
     *
     * @throws WebhookException the webhook exception
     */
    public void send() throws WebhookException {
        this.embed.put("fields", fields);
        if (this.embed.toString().length() > 6000)
            throw new WebhookException("Embed object larger than the limit (" + this.embed.toString().length() + ">6000).");

        this.obj.put("embeds", new JSONArray().put(this.embed));

        if (this.components != null && this.components.length() > 0) {
            JSONObject actionRow = new JSONObject();
            actionRow.put("type", 1);
            actionRow.put("components", this.components);
            this.obj.put("components", new JSONArray().put(actionRow));
        }

        try {
            final Jenkins instance = Jenkins.getInstanceOrNull();
            if (instance != null && instance.proxy != null && !Unirest.config().isRunning()) {
                String proxyIP = instance.proxy.name;
                int proxyPort = instance.proxy.port;
                if (!proxyIP.equals("")) {
                    Unirest.config().proxy(new Proxy(proxyIP, proxyPort));
                }
            }
            // Plain "Incoming Webhook" integrations (the kind created from a Discord
            // channel's Integrations settings) aren't tied to an application, so per
            // Discord's docs the "components" field - and therefore any buttons - is
            // silently dropped unless this query param is present. Harmless to always
            // send it, even on messages with no buttons.
            String requestUrl = this.webhookUrl
                + (this.webhookUrl.contains("?") ? "&" : "?")
                + "with_components=true";

            HttpResponse<JsonNode> response;
            if (file != null) {
                response = Unirest.post(requestUrl)
                    .field("payload_json", obj.toString())
                    .field("file", file, filename)
                    .asJson();
            } else {
                response = Unirest.post(requestUrl)
                    .field("payload_json", obj.toString())
                    .asJson();
            }

            if (response.getStatus() < 200 || response.getStatus() >= 300) {
                throw new WebhookException(response.getBody().getObject().toString(2));
            }
        } catch (UnirestException e) {
            e.printStackTrace();
        }
    }
}