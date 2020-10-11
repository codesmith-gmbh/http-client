package javatest;

import java.net.http.HttpResponse;

public class Hello {

    public static HttpResponse.BodyHandler<Object> of() {
        return (responseInfo) -> {
            var streamHandler = HttpResponse.BodyHandlers.ofInputStream();
            var subscriber = streamHandler.apply(responseInfo);

            return null;
        };
    }

}
