import { IncomingMessage, ServerResponse } from "node:http";

export interface VercelRequest extends IncomingMessage {
    body: unknown;
}

export interface VercelResponse extends ServerResponse {
    status(statusCode: number): VercelResponse;
    json(body: { status: string; message: string }): VercelResponse;
}

export function sendResponse(
    response: VercelResponse,
    httpStatus: number,
    status: string,
    message: string,
): void {
    response.status(httpStatus).json({ status, message });
}