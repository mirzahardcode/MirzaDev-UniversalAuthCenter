export interface AppConfig {
    packageName: string;
    displayName: string;
}

export const APP_ALLOWLIST: Readonly<Record<string, AppConfig>> = Object.freeze({
    "cracking-exam": Object.freeze({
        packageName: "id.web.app.semiofflinecbt",
        displayName: "Cracking Exam",
    }),
});

export function getAppConfig(appKey: string): AppConfig | undefined {
    return Object.prototype.hasOwnProperty.call(APP_ALLOWLIST, appKey)
        ? APP_ALLOWLIST[appKey]
        : undefined;
}