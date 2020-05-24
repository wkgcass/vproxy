package vproxybase.util;

import java.util.Date;

public class ErrorPages {
    private ErrorPages() {
    }

    public static String build(int code, String statusMsg, String msg, String rawIp, String xffIp) {
        return build(code + " " + statusMsg, msg, "<p> current server time " + new Date() + "</p> <p> request raw ip " + rawIp + " </p> <p> x-forwarded-for ip " + xffIp + " </p>");
    }

    public static String build(String title, String description, String message) {
        return build(title, description, message, "Build something amazing", "https://github.com/wkgcass/vproxy");
    }

    public static String build(String title, String description, String message, String buttonMsg, String buttonDirect) {
        return "<html><head> <meta charset=\"utf-8\"> <meta content=\"width=device-width,initial-scale=1.0,minimum-scale=1.0,maximum-scale=1.0,user-scalable=no\" name=\"viewport\"> <title> " + title + " </title> <link rel=\"shortcut icon\" type=\"image/x-icon\" href=\"https://github.com/fluidicon.png\"> <style>html, body {\n" +
            "  font-family: sans-serif;\n" +
            "  -ms-text-size-adjust: 100%;\n" +
            "  -webkit-text-size-adjust: 100%;\n" +
            "  background-color: #F7F8FB;\n" +
            "  height: 100%;\n" +
            "  -webkit-font-smoothing: antialiased; }\n" +
            "\n" +
            "body {\n" +
            "  margin: 0;\n" +
            "  padding: 0;\n" +
            "  display: flex;\n" +
            "  flex-direction: column;\n" +
            "  align-items: center;\n" +
            "  justify-content: center; }\n" +
            "\n" +
            ".message {\n" +
            "  text-align: center;\n" +
            "  align-self: center;\n" +
            "  display: flex;\n" +
            "  flex-direction: column;\n" +
            "  align-items: center;\n" +
            "  padding: 0px 20px;\n" +
            "  max-width: 450px; }\n" +
            "\n" +
            ".message__title {\n" +
            "  font-size: 22px;\n" +
            "  font-weight: 100;\n" +
            "  margin-top: 15px;\n" +
            "  color: #47494E;\n" +
            "  margin-bottom: 8px; }\n" +
            "\n" +
            "p {\n" +
            "  -webkit-margin-after: 0px;\n" +
            "  -webkit-margin-before: 0px;\n" +
            "  font-size: 15px;\n" +
            "  color: #7F828B;\n" +
            "  line-height: 21px;\n" +
            "  margin-bottom: 4px; }\n" +
            "\n" +
            ".btn {\n" +
            "  text-decoration: none;\n" +
            "  padding: 8px 15px;\n" +
            "  border-radius: 4px;\n" +
            "  margin-top: 10px;\n" +
            "  font-size: 14px;\n" +
            "  color: #7F828B;\n" +
            "  border: 1px solid #7F828B; }\n" +
            "\n" +
            ".hk-logo, .app-icon {\n" +
            "  fill: #DBE1EC; }\n" +
            "\n" +
            ".info {\n" +
            "  fill: #9FABBC; }\n" +
            "\n" +
            "body.friendly {\n" +
            "  background: -webkit-linear-gradient(-45deg, #8363a1 0%, #74a8c3 100%);\n" +
            "  background: linear-gradient(135deg, #8363a1 0%, #74a8c3 100%); }\n" +
            "\n" +
            "body.friendly .message__title {\n" +
            "  color: #fff; }\n" +
            "\n" +
            "body.friendly p {\n" +
            "  color: rgba(255, 255, 255, 0.6); }\n" +
            "\n" +
            "body.friendly .hk-logo, body.friendly .app-icon {\n" +
            "  fill: rgba(255, 255, 255, 0.9); }\n" +
            "\n" +
            "body.friendly .info {\n" +
            "  fill: rgba(255, 255, 255, 0.9); }\n" +
            "\n" +
            "body.friendly .btn {\n" +
            "  color: #fff;\n" +
            "  border: 1px solid rgba(255, 255, 255, 0.9); }\n" +
            "\n" +
            ".info_area {\n" +
            "  position: fixed;\n" +
            "  right: 12px;\n" +
            "  bottom: 12px; }\n" +
            "\n" +
            ".logo {\n" +
            "  position: fixed;\n" +
            "  left: 12px;\n" +
            "  bottom: 12px; }\n" +
            "</style> <base target=\"_parent\"> </head> <body class=\"friendly\"> <div class=\"spacer\"></div> <div class=\"message\"> <div class=\"message__title\"> " + title + " <br/> " + description + " <br/> " + message + " </div> <a href=\"" + buttonDirect + "\" class=\"btn\">" + buttonMsg + "</a> </div>   </body></html>\n";
    }
}
