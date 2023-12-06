import { LitElement, html, css} from 'lit';
import { pages } from 'build-time-data';
import 'qwc/qwc-extension-link.js';

export class QwcMailpitCard extends LitElement {

    static styles = css`
      .identity {
        display: flex;
        justify-content: flex-start;
      }

      .description {
        padding-bottom: 10px;
      }

      .logo {
        padding-bottom: 10px;
        margin-right: 5px;
      }

      .card-content {
        color: var(--lumo-contrast-90pct);
        display: flex;
        flex-direction: column;
        justify-content: flex-start;
        padding: 2px 2px;
        height: 100%;
      }

      .card-content slot {
        display: flex;
        flex-flow: column wrap;
        padding-top: 5px;
      }
    `;

    static properties = {
        extensionName: {attribute: true},
        description: {attribute: true},
        guide: {attribute: true},
        namespace: {attribute: true},
    };


    constructor() {
        super();
    }

    connectedCallback() {
        super.connectedCallback();
    }

    render() {
        return html`<div class="card-content" slot="content">
            <div class="identity">
                <div class="logo">
                    <img src="data:image/svg+xml;base64,PD94bWwgdmVyc2lvbj0iMS4wIiBlbmNvZGluZz0iVVRGLTgiIHN0YW5kYWxvbmU9Im5vIj8+CjxzdmcKICAgd2lkdGg9IjUwMCIKICAgaGVpZ2h0PSI0NjAiCiAgIHZpZXdCb3g9IjAgMCAxMzIuMjkyIDEyMS43MDgiCiAgIHZlcnNpb249IjEuMSIKICAgaWQ9InN2ZzYiCiAgIHhtbG5zPSJodHRwOi8vd3d3LnczLm9yZy8yMDAwL3N2ZyIKICAgeG1sbnM6c3ZnPSJodHRwOi8vd3d3LnczLm9yZy8yMDAwL3N2ZyI+CiAgPGRlZnMKICAgICBpZD0iZGVmczEwIiAvPgogIDxwYXRoCiAgICAgZD0iTTEyLjMyMSAwbDUzLjg2MSA1My45MThMMTIwLjM2NSAwek01LjE1NSA5LjAyNWw2MC44NDIgNTkuNjczIDYxLjIxMS01OS40ODktLjE4NSAzNi44MzVMNjYuOTIxIDcwLjU0bDE1LjE2NCAxMi42MTYtOC4xMzcgNS45ODYtNDEuNjA5LjE4NGMtNC44MzgtLjAyMi0yNS44NzctMTguMzQtMjcuMTg1LTQxLjI1NXoiCiAgICAgZmlsbC1vcGFjaXR5PSIuOTQxIgogICAgIGZpbGw9IiMyZDRhNWYiCiAgICAgaWQ9InBhdGgyIgogICAgIHN0eWxlPSJmaWxsOiNmZmZmZmY7ZmlsbC1vcGFjaXR5OjEiIC8+CiAgPHBhdGgKICAgICBkPSJNNzguMzg1IDcyLjA0OWw1My45MDctMjEuNjc5LTguMDMxIDU3LjMxOC0xMS44NDUtOS4xMzJjLTIxLjcyNyAyMy4xNzEtNDUuMjU1IDI2LjI4OS02Ny45OTcgMjAuODM3UzEyLjI4MSA5OC4zOSA1LjE1NSA4My44LS42NyA1My4xMTYgMi44NDMgMzguNzY5YzEuMTMgMTAuNTExLTEuMzEzIDE2LjMxNiA2LjM4IDMzLjYxMiA2LjMxIDExLjM5OSAxNC40MTMgMjAuNDE3IDI1Ljg5IDI0Ljk1NiAxMy45IDYuMTk1IDMyLjI0NyAzLjM1NyA0MS43MDEtMy4wMzlsMTQuMjQtMTIuMTU2eiIKICAgICBmaWxsPSIjMDBiNzg2IgogICAgIGlkPSJwYXRoNCIgLz4KPC9zdmc+Cg=="
                                       alt="${this.extensionName}" 
                                       title="${this.extensionName}"
                                       width="32" 
                                       height="32">
                </div>
                <div class="description">${this.description}</div>
            </div>
            ${this._renderCardLinks()}
        </div>
        `;
    }

    _renderCardLinks(){
        return html`${pages.map(page => html`
                            <qwc-extension-link slot="link"
                                namespace="${this.namespace}"
                                extensionName="${this.extensionName}"
                                iconName="${page.icon}"
                                displayName="${page.title}"
                                staticLabel="${page.staticLabel}"
                                dynamicLabel="${page.dynamicLabel}"
                                streamingLabel="${page.streamingLabel}"
                                path="${page.id}"
                                ?embed=${page.embed}
                                externalUrl="${page.metadata.externalUrl}"
                                webcomponent="${page.componentLink}" >
                            </qwc-extension-link>
                        `)}`;
    }

}
customElements.define('qwc-mailpit-card', QwcMailpitCard);