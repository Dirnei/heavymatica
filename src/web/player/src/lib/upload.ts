import { goToLanding, requestMarker, type UploadResult } from './api';

export type UploadOutcome =
  | { kind: 'stored'; status: number; result: UploadResult }
  | { kind: 'rejected'; status: number; problem: unknown }
  | { kind: 'failed' };

/** The upload address for a new schematic, put straight into `folder` unless that is empty. */
export function newSchematicUrl(name: string, folder = ''): string {
  return `/v1/schematics?name=${encodeURIComponent(name)}${folder ? `&folder=${encodeURIComponent(folder)}` : ''}`;
}

export function newVersionUrl(schematicId: string): string {
  return `/v1/schematics/${encodeURIComponent(schematicId)}/versions`;
}

/** Sends the raw file; XMLHttpRequest because fetch reports no upload progress. */
export function uploadFile(url: string, file: File, onProgress: (fraction: number) => void): Promise<UploadOutcome> {
  return new Promise((resolve) => {
    const request = new XMLHttpRequest();
    request.open('POST', url);
    request.setRequestHeader('Content-Type', 'application/octet-stream');
    request.setRequestHeader(requestMarker.name, requestMarker.value);
    request.upload.onprogress = (event) => {
      if (event.lengthComputable && event.total > 0) {
        onProgress(event.loaded / event.total);
      }
    };
    request.onload = () => {
      const body = parse(request.responseText);
      if (request.status === 401) {
        goToLanding();
      }
      resolve(
        request.status >= 200 && request.status < 300 && body
          ? { kind: 'stored', status: request.status, result: body as UploadResult }
          : { kind: 'rejected', status: request.status, problem: body },
      );
    };
    request.onerror = () => resolve({ kind: 'failed' });
    request.onabort = () => resolve({ kind: 'failed' });
    request.send(file);
  });
}

function parse(text: string): unknown {
  try {
    return text ? JSON.parse(text) : undefined;
  } catch {
    return undefined;
  }
}

/** One file in the library's upload queue. */
export interface QueuedUpload {
  key: number;
  name: string;
  folder: string;
  progress: number;
  state: 'waiting' | 'uploading' | 'done' | 'failed';
  message?: string;
}
