import { Environment , getWebsocketScheme } from "src/environments";
import { theme } from "./theme";

export const environment: Environment = {
    ...theme, ...{

        backend: "OpenEMS Edge",
        url: `${getWebsocketScheme()}://${location.hostname}/backend/`,

        production: true,
        debugMode: false,
    },
};
