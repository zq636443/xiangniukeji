let apiBaseUrl = '';

export function configureRequest(options: { baseUrl?: string }) {
  apiBaseUrl = options.baseUrl ?? '';
}

type RequestOptionsWithoutUrl = Omit<UniApp.RequestOptions, 'url'>;

export async function request<T>(url: string, options: RequestOptionsWithoutUrl = {}): Promise<T> {
  return new Promise((resolve, reject) => {
    const token = uni.getStorageSync('xniu_merchant_token');
    const header = (options.header || {}) as Record<string, string>;
    uni.request({
      ...options,
      url: `${apiBaseUrl}${url}`,
      header: {
        ...header,
        ...(token ? { Authorization: `Bearer ${token}` } : {})
      },
      success: (response) => {
        const body = response.data as { code?: number; message?: string; data?: T };
        if (body?.code === 0) {
          resolve(body.data as T);
          return;
        }
        reject(new Error(body?.message || '请求失败'));
      },
      fail: reject
    });
  });
}
