import { Environment , getWebsocketScheme } from "src/environments";
import { theme } from "./theme";

export const environment: Environment = {
    ...theme, ...{

        backend: "OpenEMS Edge",
        url: `${getWebsocketScheme()}://${location.hostname}/sock/`,

        production: true,
        debugMode: false,
    },
};
