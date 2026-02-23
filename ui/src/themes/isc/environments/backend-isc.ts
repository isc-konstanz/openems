import { Environment , getWebsocketScheme } from "src/environments";
import { theme } from "./theme";

export const environment: Environment = {
    ...theme, ...{

        backend: "OpenEMS Backend",
        url: `${getWebsocketScheme()}://${location.hostname}/backend/`,

        production: true,
        debugMode: false,
    },
};
