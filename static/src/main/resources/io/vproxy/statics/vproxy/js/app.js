const API_BASE = (() => {
  const saved = localStorage.getItem('vproxy-api-base');
  if (saved) return saved;
  const proto = location.protocol;
  if (proto === 'http:' || proto === 'https:') {
    return proto + '//' + location.host;
  }
  return 'http://127.0.0.1:18776';
})();

async function apiRequest(method, path, body) {
  const url = API_BASE + '/api/v1/module' + path;
  const opts = {
    method,
    headers: { 'Content-Type': 'application/json' },
  };
  if (body !== undefined) opts.body = JSON.stringify(body);
  const res = await fetch(url, opts);
  if (res.status === 204) return null;
  if (!res.ok) {
    let msg = `${method} ${path} failed: ${res.status}`;
    try {
      const err = await res.json();
      if (err.message) msg = err.message;
    } catch {}
    throw new Error(msg);
  }
  return res.json();
}

const MODULE_CONFIG = {
  'tcp-lb': {
    title: 'TCP Load Balancer',
    path: '/tcp-lb',
    idField: 'name',
    parent: null,
    columns: [
      { key: 'name', label: 'Name' },
      { key: 'address', label: 'Address' },
      { key: 'backend', label: 'Backend' },
      { key: 'protocol', label: 'Protocol' },
      { key: 'inBufferSize', label: 'In Buffer' },
      { key: 'outBufferSize', label: 'Out Buffer' },
    ],
    createFields: [
      { key: 'name', label: 'Name', type: 'text', required: true },
      { key: 'address', label: 'Address', type: 'text', required: true, hint: 'Binding l4addr' },
      { key: 'backend', label: 'Backend', type: 'remote-select', required: true, hint: 'Upstream reference', remoteModule: 'upstream' },
      { key: 'protocol', label: 'Protocol', type: 'select', options: ['tcp','http','h2','http/1.x','framed-int32','dubbo'] },
      { key: 'workerLoopGroup', label: 'Worker Loop Group', type: 'select', hint: 'Event loop group for handling netflow' },
      { key: 'inBufferSize', label: 'Input Buffer Size', type: 'number' },
      { key: 'outBufferSize', label: 'Output Buffer Size', type: 'number' },
      { key: 'listOfCertKey', label: 'Certificates', type: 'remote-tags', hint: 'Cert-key references for TLS', remoteModule: 'cert-key' },
      { key: 'securityGroup', label: 'Security Group', type: 'remote-select', hint: 'Security group reference', remoteModule: 'security-group' },
    ],
    updateFields: [
      { key: 'inBufferSize', label: 'Input Buffer Size', type: 'number' },
      { key: 'outBufferSize', label: 'Output Buffer Size', type: 'number' },
      { key: 'listOfCertKey', label: 'Certificates', type: 'remote-tags', hint: 'Cert-key references for TLS', remoteModule: 'cert-key' },
      { key: 'securityGroup', label: 'Security Group', type: 'remote-select', hint: 'Security group reference', remoteModule: 'security-group' },
    ],
    hasDetail: true,
  },
  'socks5-server': {
    title: 'SOCKS5 Server',
    path: '/socks5-server',
    idField: 'name',
    parent: null,
    columns: [
      { key: 'name', label: 'Name' },
      { key: 'address', label: 'Address' },
      { key: 'backend', label: 'Backend' },
      { key: 'inBufferSize', label: 'In Buffer' },
      { key: 'outBufferSize', label: 'Out Buffer' },
      { key: 'allowNonBackend', label: 'Allow Non-Backend' },
    ],
    createFields: [
      { key: 'name', label: 'Name', type: 'text', required: true },
      { key: 'address', label: 'Address', type: 'text', required: true, hint: 'Binding l4addr' },
      { key: 'backend', label: 'Backend', type: 'remote-select', required: true, hint: 'Upstream reference', remoteModule: 'upstream' },
      { key: 'acceptorLoopGroup', label: 'Acceptor Loop Group', type: 'select', hint: 'Event loop group for accepting connections' },
      { key: 'workerLoopGroup', label: 'Worker Loop Group', type: 'select', hint: 'Event loop group for handling netflow' },
      { key: 'inBufferSize', label: 'Input Buffer Size', type: 'number' },
      { key: 'outBufferSize', label: 'Output Buffer Size', type: 'number' },
      { key: 'securityGroup', label: 'Security Group', type: 'remote-select', hint: 'Security group reference', remoteModule: 'security-group' },
      { key: 'allowNonBackend', label: 'Allow Non-Backend', type: 'checkbox', hint: 'Allow proxy to non-backend endpoints' },
    ],
    updateFields: [
      { key: 'inBufferSize', label: 'Input Buffer Size', type: 'number' },
      { key: 'outBufferSize', label: 'Output Buffer Size', type: 'number' },
      { key: 'securityGroup', label: 'Security Group', type: 'remote-select', hint: 'Security group reference', remoteModule: 'security-group' },
      { key: 'allowNonBackend', label: 'Allow Non-Backend', type: 'checkbox', hint: 'Allow proxy to non-backend endpoints' },
    ],
    hasDetail: true,
  },
  'dns-server': {
    title: 'DNS Server',
    path: '/dns-server',
    idField: 'name',
    parent: null,
    columns: [
      { key: 'name', label: 'Name' },
      { key: 'address', label: 'Address' },
      { key: 'rrsets', label: 'RRSets' },
      { key: 'ttl', label: 'TTL' },
      { key: 'eventLoopGroup', label: 'Event Loop Group' },
      { key: 'securityGroup', label: 'Security Group' },
    ],
    createFields: [
      { key: 'name', label: 'Name', type: 'text', required: true },
      { key: 'address', label: 'Address', type: 'text', required: true, hint: 'Binding l4addr' },
      { key: 'rrsets', label: 'RRSets', type: 'remote-select', required: true, hint: 'Upstream reference', remoteModule: 'upstream' },
      { key: 'ttl', label: 'TTL', type: 'number', hint: 'TTL of answer records' },
      { key: 'eventLoopGroup', label: 'Event Loop Group', type: 'select', hint: 'Reference to running event loop group' },
      { key: 'securityGroup', label: 'Security Group', type: 'remote-select', hint: 'Security group for the DNS server', remoteModule: 'security-group' },
    ],
    updateFields: [
      { key: 'ttl', label: 'TTL', type: 'number', hint: 'TTL of answer records' },
      { key: 'securityGroup', label: 'Security Group', type: 'remote-select', hint: 'Security group for the DNS server', remoteModule: 'security-group' },
    ],
    hasDetail: true,
  },
  'event-loop-group': {
    title: 'Event Loop Group',
    path: '/event-loop-group',
    idField: 'name',
    parent: null,
    columns: [{ key: 'name', label: 'Name' }],
    createFields: [
      { key: 'name', label: 'Name', type: 'text', required: true },
      { key: 'annotations', label: 'Annotations', type: 'kv', hint: 'Key-value annotations', keyOptions: ['vproxy/event-loop-group-prefer-poll'] },
    ],
    updateFields: [
      { key: 'annotations', label: 'Annotations', type: 'kv', hint: 'Key-value annotations', keyOptions: ['vproxy/event-loop-group-prefer-poll'] },
    ],
    hasDetail: true,
    childModules: [
      { key: 'event-loop', label: 'Event Loops', param: 'elg', href: 'event-loop.html', icon: '➰' },
    ],
  },
  'event-loop': {
    title: 'Event Loop',
    path: '/event-loop-group/{elg}/event-loop',
    idField: 'name',
    parent: { key: 'elg', label: 'Event Loop Group', module: 'event-loop-group' },
    columns: [{ key: 'name', label: 'Name' }],
    createFields: [
        { key: 'name', label: 'Name', type: 'text', required: true },
        { key: 'annotations', label: 'Annotations', type: 'kv', hint: 'Key-value annotations', keyOptions: ['vproxy/event-loop-core-affinity'] },
    ],
    updateFields: [],
    hasDetail: true,
  },
  'upstream': {
    title: 'Upstream',
    path: '/upstream',
    idField: 'name',
    parent: null,
    columns: [{ key: 'name', label: 'Name' }],
    createFields: [{ key: 'name', label: 'Name', type: 'text', required: true }],
    updateFields: [],
    hasDetail: true,
    childModules: [
      { key: 'server-group-in-upstream', label: 'Server Groups', param: 'ups', href: 'server-group-in-upstream.html', icon: '🔗' },
    ],
  },
  'server-group': {
    title: 'Server Group',
    path: '/server-group',
    idField: 'name',
    parent: null,
    columns: [
      { key: 'name', label: 'Name' },
      { key: 'timeout', label: 'Timeout' },
      { key: 'period', label: 'Period' },
      { key: 'up', label: 'Up' },
      { key: 'down', label: 'Down' },
      { key: 'protocol', label: 'Protocol' },
      { key: 'method', label: 'Method' },
      { key: 'eventLoopGroup', label: 'Event Loop Group' },
    ],
    createFields: [
      { key: 'name', label: 'Name', type: 'text', required: true },
      { key: 'timeout', label: 'Timeout (ms)', type: 'number', required: true, hint: 'Health check timeout' },
      { key: 'period', label: 'Period (ms)', type: 'number', required: true, hint: 'Health check period' },
      { key: 'up', label: 'Up', type: 'number', required: true, hint: 'Healthy after N successful checks' },
      { key: 'down', label: 'Down', type: 'number', required: true, hint: 'Unhealthy after N failed checks' },
      { key: 'protocol', label: 'Check Protocol', type: 'select', options: ['tcp','http','dns','tcpDelay','none'], hint: 'Protocol for health check' },
      { key: 'method', label: 'Method', type: 'select', options: ['wrr','wlc','source'], hint: 'Load balancing method' },
      { key: 'annotations', label: 'Annotations', type: 'kv', hint: 'Key-value annotations', keyOptions: ['vproxy/hc-http-method','vproxy/hc-http-url','vproxy/hc-http-host','vproxy/hc-http-status','vproxy/hc-dns-domain'] },
      { key: 'eventLoopGroup', label: 'Event Loop Group', type: 'select', hint: 'Event loop group for health checks' },
    ],
    updateFields: [
      { key: 'timeout', label: 'Timeout (ms)', type: 'number', hint: 'Health check timeout' },
      { key: 'period', label: 'Period (ms)', type: 'number', hint: 'Health check period' },
      { key: 'up', label: 'Up', type: 'number', hint: 'Healthy after N successful checks' },
      { key: 'down', label: 'Down', type: 'number', hint: 'Unhealthy after N failed checks' },
      { key: 'protocol', label: 'Check Protocol', type: 'select', options: ['tcp','http','dns','tcpDelay','none'], hint: 'Protocol for health check' },
      { key: 'method', label: 'Method', type: 'select', options: ['wrr','wlc','source'], hint: 'Load balancing method' },
      { key: 'annotations', label: 'Annotations', type: 'kv', hint: 'Key-value annotations', keyOptions: ['vproxy/hc-http-method','vproxy/hc-http-url','vproxy/hc-http-host','vproxy/hc-http-status','vproxy/hc-dns-domain'] },
    ],
    hasDetail: true,
    childModules: [
      { key: 'server', label: 'Servers', param: 'sg', href: 'server.html', icon: '🖥️' },
    ],
  },
  'server-group-in-upstream': {
    title: 'Server Group in Upstream',
    path: '/upstream/{ups}/server-group',
    idField: 'name',
    parent: { key: 'ups', label: 'Upstream', module: 'upstream' },
    columns: [
      { key: 'name', label: 'Name' },
      { key: 'weight', label: 'Weight' },
    ],
    createFields: [
      { key: 'name', label: 'Name', type: 'remote-select', required: true, remoteModule: 'server-group' },
      { key: 'weight', label: 'Weight', type: 'number', hint: 'Weight in upstream' },
      { key: 'annotations', label: 'Annotations', type: 'kv', hint: 'Key-value annotations', keyOptions: ['vproxy/hint-host','vproxy/hint-port','vproxy/hint-uri'] },
    ],
    updateFields: [
      { key: 'weight', label: 'Weight', type: 'number', hint: 'Weight in upstream' },
      { key: 'annotations', label: 'Annotations', type: 'kv', hint: 'Key-value annotations', keyOptions: ['vproxy/hint-host','vproxy/hint-port','vproxy/hint-uri'] },
    ],
    hasDetail: true,
    createLabel: 'Add',
    deleteLabel: 'Remove',
    createButtonText: 'Add Server Group to Upstream',
  },
  'server': {
    title: 'Server',
    path: '/server-group/{sg}/server',
    idField: 'name',
    parent: { key: 'sg', label: 'Server Group', module: 'server-group' },
    columns: [
      { key: 'name', label: 'Name' },
      { key: 'address', label: 'Address' },
      { key: 'weight', label: 'Weight' },
      { key: 'status', label: 'Status' },
      { key: 'cost', label: 'Cost' },
    ],
    createFields: [
      { key: 'name', label: 'Name', type: 'text', required: true },
      { key: 'address', label: 'Address', type: 'text', required: true, hint: 'l4addr or hostname:port' },
      { key: 'weight', label: 'Weight', type: 'number', hint: 'Weight in server group' },
    ],
    updateFields: [
      { key: 'weight', label: 'Weight', type: 'number', hint: 'Weight in server group' },
    ],
    hasDetail: true,
  },
  'security-group': {
    title: 'Security Group',
    path: '/security-group',
    idField: 'name',
    parent: null,
    columns: [
      { key: 'name', label: 'Name' },
      { key: 'defaultRule', label: 'Default Rule' },
    ],
    createFields: [
      { key: 'name', label: 'Name', type: 'text', required: true },
      { key: 'defaultRule', label: 'Default Rule', type: 'select', required: true, options: ['allow','deny'] },
    ],
    updateFields: [
      { key: 'defaultRule', label: 'Default Rule', type: 'select', options: ['allow','deny'] },
    ],
    hasDetail: true,
    childModules: [
      { key: 'security-group-rule', label: 'Rules', param: 'secg', href: 'security-group-rule.html', icon: '📜' },
    ],
  },
  'security-group-rule': {
    title: 'Security Group Rule',
    path: '/security-group/{secg}/security-group-rule',
    idField: 'name',
    parent: { key: 'secg', label: 'Security Group', module: 'security-group' },
    columns: [
      { key: 'name', label: 'Name' },
      { key: 'clientNetwork', label: 'Client Network' },
      { key: 'protocol', label: 'Protocol' },
      { key: 'serverPortMin', label: 'Port Min' },
      { key: 'serverPortMax', label: 'Port Max' },
      { key: 'rule', label: 'Rule' },
    ],
    createFields: [
      { key: 'name', label: 'Name', type: 'text', required: true },
      { key: 'clientNetwork', label: 'Client Network', type: 'text', required: true, hint: 'Network CIDR' },
      { key: 'protocol', label: 'Protocol', type: 'select', required: true, options: ['TCP','UDP'] },
      { key: 'serverPortMin', label: 'Server Port Min', type: 'number', required: true },
      { key: 'serverPortMax', label: 'Server Port Max', type: 'number', required: true },
      { key: 'rule', label: 'Rule', type: 'select', required: true, options: ['allow','deny'] },
    ],
    updateFields: [],
    hasDetail: true,
  },
  'cert-key': {
    title: 'Cert Key',
    path: '/cert-key',
    idField: 'name',
    parent: null,
    columns: [
      { key: 'name', label: 'Name' },
      { key: 'certs', label: 'Certificates' },
      { key: 'key', label: 'Key' },
    ],
    createFields: [
      { key: 'name', label: 'Name', type: 'text', required: true },
      { key: 'certs', label: 'Certificate Files', type: 'tags', required: true, hint: 'File paths of certificates' },
      { key: 'key', label: 'Key File', type: 'text', required: true, hint: 'File path of the key' },
    ],
    updateFields: [],
    hasDetail: true,
    createButtonClass: 'btn-secondary',
    extraActions: [
      { key: 'createPem', label: 'Create from PEM', modalTitle: 'Create Cert-Key from PEM', primary: true },
    ],
  },
};

function buildPath(config, parents, id) {
  let p = config.path;
  if (config.parent) {
    p = p.replace(`{${config.parent.key}}`, encodeURIComponent(parents[config.parent.key] || ''));
  }
  if (id !== undefined && id !== null) {
    p = p + '/' + encodeURIComponent(id);
  }
  return p;
}

function formatValue(val) {
  if (val === null || val === undefined) return '—';
  if (typeof val === 'boolean') return val ? 'Yes' : 'No';
  if (Array.isArray(val)) return val.length ? val.join(', ') : '—';
  if (typeof val === 'object') return JSON.stringify(val);
  return String(val);
}

function columnValue(row, key) {
  const val = row[key];
  if (key === 'status') {
    const cls = val === 'UP' ? 'chip-success' : val === 'DOWN' ? 'chip-error' : '';
    return `<span class="chip ${cls}">${val || '—'}</span>`;
  }
  if (key === 'allowNonBackend') {
    return `<span class="chip ${val ? 'chip-error' : 'chip-success'}">${val ? 'Yes' : 'No'}</span>`;
  }
  if (key === 'defaultRule' || key === 'rule') {
    const cls = val === 'allow' ? 'chip-success' : val === 'deny' ? 'chip-error' : '';
    return `<span class="chip ${cls}">${val || '—'}</span>`;
  }
  if (key === 'annotations') {
    if (!val || typeof val !== 'object' || Object.keys(val).length === 0) return '—';
    const items = Object.entries(val).map(([k, v]) => `<div class="kv-item"><span class="kv-key">${k}</span><span class="kv-val">${v}</span></div>`).join('');
    return `<div class="kv-list">${items}</div>`;
  }
  const v = formatValue(val);
  const text = v.length > 40 ? v.slice(0, 40) + '…' : v;
  return text;
}

const Toast = {
  toasts: Vue.reactive([]),
  push(message, type = 'success', duration = 3000) {
    const id = Date.now() + Math.random();
    this.toasts.push({ id, message, type });
    setTimeout(() => {
      const idx = this.toasts.findIndex(t => t.id === id);
      if (idx > -1) this.toasts.splice(idx, 1);
    }, duration);
  },
  success(msg) { this.push(msg, 'success'); },
  error(msg) { this.push(msg, 'error', 5000); },
};

function initCrudApp(moduleKey, mountEl) {
  const config = MODULE_CONFIG[moduleKey];
  if (!config) throw new Error('Unknown module: ' + moduleKey);

  const initialParents = {};
  const url = new URL(location.href);
  if (config.parent) {
    initialParents[config.parent.key] = url.searchParams.get(config.parent.key) || '';
  }

  const app = Vue.createApp({
    setup() {
      const items = Vue.ref([]);
      const loading = Vue.ref(false);
      const search = Vue.ref('');
      const parents = Vue.reactive({ ...initialParents });
      const parentOptions = Vue.ref([]);

      const showCreate = Vue.ref(false);
      const showEdit = Vue.ref(false);
      const showDetail = Vue.ref(false);
      const showExtra = Vue.ref(null); // { key, title }
      const currentItem = Vue.ref({});
      const detailData = Vue.ref(null);

      const createForm = Vue.reactive({});
      const editForm = Vue.reactive({});
      const extraForm = Vue.reactive({});
      const tagInputs = Vue.reactive({});
      const kvInputs = Vue.reactive({});
      const remoteOptions = Vue.reactive({});

      function resetForm(target, fields) {
        for (const k in target) delete target[k];
        for (const f of fields) {
          if (f.type === 'tags' || f.type === 'remote-tags') target[f.key] = [];
          else if (f.type === 'kv') target[f.key] = {};
          else if (f.type === 'textlist') target[f.key] = [''];
          else if (f.type === 'checkbox') target[f.key] = false;
          else if (f.type === 'number') target[f.key] = null;
          else target[f.key] = '';
        }
        for (const k in tagInputs) delete tagInputs[k];
        for (const k in kvInputs) delete kvInputs[k];
      }

      function loadForm(target, data, fields) {
        for (const k in target) delete target[k];
        for (const f of fields) {
          let v = data[f.key];
          if (f.type === 'tags' || f.type === 'remote-tags') target[f.key] = Array.isArray(v) ? [...v] : [];
          else if (f.type === 'kv') target[f.key] = v && typeof v === 'object' ? { ...v } : {};
          else if (f.type === 'checkbox') target[f.key] = !!v;
          else if (f.type === 'number') target[f.key] = v === undefined || v === null ? null : Number(v);
          else target[f.key] = v === undefined || v === null ? '' : String(v);
        }
      }

      async function fetchParents() {
        if (!config.parent) return;
        try {
          const list = await apiRequest('GET', buildPath(MODULE_CONFIG[config.parent.module], {}, null));
          parentOptions.value = Array.isArray(list) ? list : [];
          if (parentOptions.value.length && !parents[config.parent.key]) {
            parents[config.parent.key] = parentOptions.value[0][MODULE_CONFIG[config.parent.module].idField];
          }
        } catch (e) {
          Toast.error('Failed to load parent options: ' + e.message);
        }
      }

      async function loadItems() {
        if (config.parent && !parents[config.parent.key]) {
          items.value = [];
          return;
        }
        loading.value = true;
        try {
          const list = await apiRequest('GET', buildPath(config, parents, null));
          items.value = Array.isArray(list) ? list : [];
        } catch (e) {
          Toast.error(e.message);
          items.value = [];
        } finally {
          loading.value = false;
        }
      }

      const filteredItems = Vue.computed(() => {
        const s = search.value.trim().toLowerCase();
        if (!s) return items.value;
        return items.value.filter(it => {
          return config.columns.some(col => {
            const v = String(it[col.key] || '').toLowerCase();
            return v.includes(s);
          });
        });
      });

      async function doCreate() {
        const body = {};
        for (const f of config.createFields) {
          if (f.type === 'number' && createForm[f.key] !== null && createForm[f.key] !== '') {
            body[f.key] = Number(createForm[f.key]);
          } else if (f.type === 'checkbox') {
            body[f.key] = !!createForm[f.key];
          } else if (f.type === 'tags' || f.type === 'remote-tags') {
            body[f.key] = createForm[f.key] || [];
          } else if (f.type === 'kv') {
            const kv = createForm[f.key] || {};
            const pendingKey = (kvInputs['k-'+f.key] || '').trim();
            const pendingVal = (kvInputs['v-'+f.key] || '').trim();
            if (pendingKey) kv[pendingKey] = pendingVal;
            body[f.key] = kv;
          } else if (createForm[f.key] !== '' && createForm[f.key] !== null) {
            body[f.key] = createForm[f.key];
          }
        }
        try {
          await apiRequest('POST', buildPath(config, parents, null), body);
          Toast.success('Created successfully');
          showCreate.value = false;
          await loadItems();
        } catch (e) {
          Toast.error(e.message);
        }
      }

      async function doUpdate() {
        const id = currentItem.value[config.idField];
        const body = {};
        for (const f of config.updateFields) {
          if (f.type === 'number' && editForm[f.key] !== null && editForm[f.key] !== '') {
            body[f.key] = Number(editForm[f.key]);
          } else if (f.type === 'checkbox') {
            body[f.key] = !!editForm[f.key];
          } else if (f.type === 'tags' || f.type === 'remote-tags') {
            body[f.key] = editForm[f.key] || [];
          } else if (f.type === 'kv') {
            const kv = editForm[f.key] || {};
            const pendingKey = (kvInputs['k-'+f.key] || '').trim();
            const pendingVal = (kvInputs['v-'+f.key] || '').trim();
            if (pendingKey) kv[pendingKey] = pendingVal;
            body[f.key] = kv;
          } else if (editForm[f.key] !== '' && editForm[f.key] !== null) {
            body[f.key] = editForm[f.key];
          }
        }
        try {
          await apiRequest('PUT', buildPath(config, parents, id), body);
          Toast.success('Updated successfully');
          showEdit.value = false;
          await loadItems();
        } catch (e) {
          Toast.error(e.message);
        }
      }

      async function doDelete(item) {
        if (!confirm(`Delete "${item[config.idField]}"?`)) return;
        try {
          await apiRequest('DELETE', buildPath(config, parents, item[config.idField]));
          Toast.success('Deleted successfully');
          await loadItems();
        } catch (e) {
          Toast.error(e.message);
        }
      }

      async function openEdit(item) {
        currentItem.value = item;
        loadForm(editForm, item, config.updateFields);
        showEdit.value = true;
      }

      async function openDetail(item) {
        currentItem.value = item;
        detailData.value = null;
        showDetail.value = true;
        try {
          detailData.value = await apiRequest('GET', buildPath(config, parents, item[config.idField]) + '/detail');
        } catch (e) {
          Toast.error(e.message);
        }
      }

      async function doExtraAction() {
        if (showExtra.value.key === 'createPem') {
          const body = {};
          for (const f of [
            { key: 'name', type: 'text' },
            { key: 'certs', type: 'textlist' },
            { key: 'key', type: 'text' },
          ]) {
            if (f.type === 'tags') body[f.key] = extraForm[f.key] || [];
            else if (f.type === 'textlist') body[f.key] = (extraForm[f.key] || []).filter(s => s.trim());
            else body[f.key] = extraForm[f.key] || '';
          }
          try {
            await apiRequest('POST', '/cert-key/pem', body);
            Toast.success('Created from PEM successfully');
            showExtra.value = null;
            await loadItems();
          } catch (e) {
            Toast.error(e.message);
          }
        }
      }

      function openCreate() {
        resetForm(createForm, config.createFields);
        showCreate.value = true;
      }

      function openExtra(key, title) {
        resetForm(extraForm, [
          { key: 'name', type: 'text' },
          { key: 'certs', type: 'textlist' },
          { key: 'key', type: 'textarea' },
        ]);
        showExtra.value = { key, title };
      }

      function addTag(fieldKey, targetForm, inputKey) {
        const val = (tagInputs[inputKey] || '').trim();
        if (!val) return;
        if (!targetForm[fieldKey]) targetForm[fieldKey] = [];
        if (!targetForm[fieldKey].includes(val)) {
          targetForm[fieldKey].push(val);
        }
        tagInputs[inputKey] = '';
      }

      function removeTag(fieldKey, targetForm, idx) {
        if (targetForm[fieldKey]) targetForm[fieldKey].splice(idx, 1);
      }

      function addKv(fieldKey, targetForm, kKey, vKey) {
        const k = (kvInputs[kKey] || '').trim();
        const v = (kvInputs[vKey] || '').trim();
        if (!k) return;
        if (!targetForm[fieldKey]) targetForm[fieldKey] = {};
        targetForm[fieldKey][k] = v;
        kvInputs[kKey] = '';
        kvInputs[vKey] = '';
      }

      function removeKv(fieldKey, targetForm, k) {
        if (targetForm[fieldKey]) delete targetForm[fieldKey][k];
      }

      Vue.watch(() => parents[config.parent?.key], () => {
        loadItems();
      });

      async function loadSelectOptions() {
        const elgFields = [
          ...(config.createFields || []),
          ...(config.updateFields || []),
        ].filter(f => f.type === 'select' && (f.key === 'eventLoopGroup' || f.key === 'acceptorLoopGroup' || f.key === 'workerLoopGroup'));
        if (elgFields.length) {
          try {
            const list = await apiRequest('GET', buildPath(MODULE_CONFIG['event-loop-group'], {}, null));
            const names = (Array.isArray(list) ? list : []).map(i => i.name);
            for (const f of elgFields) f.options = names;
          } catch (e) {
            for (const f of elgFields) f.options = [];
          }
        }

        const remoteSelectFields = [
          ...(config.createFields || []),
          ...(config.updateFields || []),
        ].filter(f => (f.type === 'remote-select' || f.type === 'remote-tags') && f.remoteModule);
        for (const f of remoteSelectFields) {
          try {
            const list = await apiRequest('GET', buildPath(MODULE_CONFIG[f.remoteModule], {}, null));
            remoteOptions[f.key] = (Array.isArray(list) ? list : []).map(i => i[MODULE_CONFIG[f.remoteModule].idField]);
          } catch (e) {
            remoteOptions[f.key] = [];
          }
        }
      }

      let autoReloadInterval = null;
      function onKeydown(e) {
        if (e.key === 'Escape') {
          if (showDetail.value) showDetail.value = false;
          else if (showEdit.value) showEdit.value = false;
          else if (showCreate.value) showCreate.value = false;
          else if (showExtra.value) showExtra.value = null;
        }
      }
      Vue.onMounted(async () => {
        document.addEventListener('keydown', onKeydown);
        await loadSelectOptions();
        await fetchParents();
        await loadItems();
        if (moduleKey === 'server') {
          autoReloadInterval = setInterval(() => {
            loadItems();
          }, 5000);
        }
      });
      Vue.onUnmounted(() => {
        document.removeEventListener('keydown', onKeydown);
        if (autoReloadInterval) {
          clearInterval(autoReloadInterval);
          autoReloadInterval = null;
        }
      });

      return {
        MODULE_CONFIG, config, items, loading, search, parents, parentOptions,
        showCreate, showEdit, showDetail, showExtra,
        currentItem, detailData, createForm, editForm, extraForm,
        tagInputs, kvInputs, remoteOptions, filteredItems,
        openCreate, doCreate, openEdit, doUpdate, doDelete, openDetail,
        doExtraAction, openExtra, formatValue, columnValue,
        addTag, removeTag, addKv, removeKv, Toast,
      };
    },
    template: `
      <div class="main-content">
        <div class="container">
          <div class="breadcrumb">
            <a href="index.html">Home</a>
            <span>/</span>
            <a v-if="config.parent" :href="config.parent.module + '.html'">{{ MODULE_CONFIG[config.parent.module].title }}</a>
            <span v-if="config.parent">/</span>
            <span>{{ config.title }}</span>
          </div>
          <div class="page-header">
            <div>
              <h2 class="page-title">{{ config.title }}</h2>
              <p class="page-desc" v-if="config.parent">Manage {{ config.title }}</p>
            </div>
            <div style="display:flex;gap:10px;">
              <button class="btn btn-md" :class="config.createButtonClass || 'btn-primary'" @click="openCreate">{{ (config.createButtonClass || 'btn-primary') === 'btn-primary' ? '+ ' : '' }}{{ config.createButtonText || (config.createLabel || 'Create') + ' ' + config.title }}</button>
              <button v-for="act in (config.extraActions||[])" :key="act.key" class="btn btn-md" :class="act.primary ? 'btn-primary' : 'btn-secondary'" @click="openExtra(act.key, act.modalTitle)">
                {{ act.primary ? '+ ' : '' }}{{ act.label }}
              </button>
            </div>
          </div>

          <div v-if="config.parent" class="form-group" style="max-width:320px;margin-bottom:20px;">
            <label class="form-label">{{ config.parent.label }}</label>
            <select class="form-select" v-model="parents[config.parent.key]">
              <option v-for="opt in parentOptions" :value="opt[MODULE_CONFIG[config.parent.module].idField]">
                {{ opt[MODULE_CONFIG[config.parent.module].idField] }}
              </option>
            </select>
          </div>

          <div class="toolbar">
            <div class="search-box">
              <input class="form-input" v-model="search" placeholder="Search..." />
            </div>
            <div class="chip" v-if="!loading">{{ filteredItems.length }} items</div>
            <div class="spinner" v-else></div>
          </div>

          <div class="card">
            <div class="data-table-wrap">
              <table class="data-table">
                <thead>
                  <tr>
                    <th v-for="col in config.columns" :key="col.key">{{ col.label }}</th>
                    <th style="text-align:right;">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="item in filteredItems" :key="item[config.idField]">
                    <td v-for="col in config.columns" :key="col.key" v-html="columnValue(item, col.key)"></td>
                    <td>
                      <div class="actions">
                        <a v-for="child in (config.childModules||[])" :key="child.key" class="btn btn-primary btn-sm"
                           :href="child.href + '?' + child.param + '=' + encodeURIComponent(item[config.idField])">
                          {{ child.icon }} {{ child.label }}
                        </a>
                        <button v-if="config.hasDetail" class="btn btn-ghost btn-sm" @click="openDetail(item)">Detail</button>
                        <button v-if="config.updateFields.length" class="btn btn-secondary btn-sm" @click="openEdit(item)">Edit</button>
                        <button class="btn btn-danger btn-sm" @click="doDelete(item)">{{ config.deleteLabel || 'Delete' }}</button>
                      </div>
                    </td>
                  </tr>
                  <tr v-if="!filteredItems.length">
                    <td :colspan="config.columns.length + 1">
                      <div class="empty-state">
                        <div class="empty-state-icon">📭</div>
                        <div>No items found</div>
                      </div>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </div>

      <!-- Create Modal -->
      <div v-if="showCreate" class="modal-overlay" @click.self="showCreate = false">
        <div class="modal">
          <div class="modal-header">
            <div class="modal-title">Create {{ config.title }}</div>
            <button class="modal-close" @click="showCreate = false">&times;</button>
          </div>
          <div class="modal-body">
            <form-fields :fields="config.createFields" :form="createForm" :tags="tagInputs" :kvs="kvInputs" :remote-options="remoteOptions"
                         @add-tag="(f,i,t)=>addTag(f,createForm,i)" @remove-tag="(f,i)=>removeTag(f,createForm,i)"
                         @add-kv="(f,k,v)=>addKv(f,createForm,k,v)" @remove-kv="(f,k)=>removeKv(f,createForm,k)" />
          </div>
          <div class="modal-footer">
            <button class="btn btn-secondary btn-md" @click="showCreate = false">Cancel</button>
            <button class="btn btn-primary btn-md" @click="doCreate">Create</button>
          </div>
        </div>
      </div>

      <!-- Edit Modal -->
      <div v-if="showEdit" class="modal-overlay" @click.self="showEdit = false">
        <div class="modal">
          <div class="modal-header">
            <div class="modal-title">Edit {{ config.title }}</div>
            <button class="modal-close" @click="showEdit = false">&times;</button>
          </div>
          <div class="modal-body">
            <form-fields :fields="config.updateFields" :form="editForm" :tags="tagInputs" :kvs="kvInputs" :remote-options="remoteOptions"
                         @add-tag="(f,i,t)=>addTag(f,editForm,i)" @remove-tag="(f,i)=>removeTag(f,editForm,i)"
                         @add-kv="(f,k,v)=>addKv(f,editForm,k,v)" @remove-kv="(f,k)=>removeKv(f,editForm,k)" />
          </div>
          <div class="modal-footer">
            <button class="btn btn-secondary btn-md" @click="showEdit = false">Cancel</button>
            <button class="btn btn-primary btn-md" @click="doUpdate">Save</button>
          </div>
        </div>
      </div>

      <!-- Extra Modal (e.g. PEM) -->
      <div v-if="showExtra" class="modal-overlay" @click.self="showExtra = null">
        <div class="modal">
          <div class="modal-header">
            <div class="modal-title">{{ showExtra.title }}</div>
            <button class="modal-close" @click="showExtra = null">&times;</button>
          </div>
          <div class="modal-body">
            <form-fields :fields="[
              {key:'name',label:'Name',type:'text',required:true},
              {key:'certs',label:'Certificate PEMs',type:'textlist',required:true,hint:'Paste PEM content'},
              {key:'key',label:'Key PEM',type:'textarea',required:true,hint:'Paste key PEM content'}
            ]" :form="extraForm" :tags="tagInputs" :kvs="kvInputs" :remote-options="remoteOptions"
                         @add-tag="(f,i,t)=>addTag(f,extraForm,i)" @remove-tag="(f,i)=>removeTag(f,extraForm,i)"
                         @add-kv="(f,k,v)=>addKv(f,extraForm,k,v)" @remove-kv="(f,k)=>removeKv(f,extraForm,k)" />
          </div>
          <div class="modal-footer">
            <button class="btn btn-secondary btn-md" @click="showExtra = null">Cancel</button>
            <button class="btn btn-primary btn-md" @click="doExtraAction">Create</button>
          </div>
        </div>
      </div>

      <!-- Detail Modal -->
      <div v-if="showDetail" class="modal-overlay" @click.self="showDetail = false">
        <div class="modal" style="max-width:720px;">
          <div class="modal-header">
            <div class="modal-title">{{ config.title }} Detail</div>
            <button class="modal-close" @click="showDetail = false">&times;</button>
          </div>
          <div class="modal-body">
            <div v-if="!detailData" class="empty-state"><div class="spinner"></div> Loading...</div>
            <div v-else>
              <div class="detail-section">
                <div class="detail-grid">
                  <template v-for="(value,key) in detailData" :key="key">
                    <div class="detail-item" v-if="typeof value !== 'object' || value === null">
                      <div class="detail-label">{{ key }}</div>
                      <div class="detail-value">{{ formatValue(value) }}</div>
                    </div>
                  </template>
                  <template v-for="(value,key) in detailData" :key="key">
                    <div class="detail-item" v-if="typeof value === 'object' && value !== null" style="grid-column: 1 / -1;">
                      <div class="detail-label">{{ key }}</div>
                      <div class="detail-value mono" v-if="key !== 'annotations'">
                        <pre class="json-pre">{{ JSON.stringify(value, null, 2) }}</pre>
                      </div>
                      <div class="detail-value" v-else>
                        <div class="kv-list" v-if="value && Object.keys(value).length">
                          <div class="kv-item" v-for="(v,k) in value" :key="k">
                            <span class="kv-key">{{ k }}</span>
                            <span class="kv-val">{{ v }}</span>
                          </div>
                        </div>
                        <div v-else>—</div>
                      </div>
                    </div>
                  </template>
                </div>
              </div>
            </div>
          </div>
          <div class="modal-footer">
            <button class="btn btn-secondary btn-md" @click="showDetail = false">Close</button>
          </div>
        </div>
      </div>

      <!-- Toasts -->
      <div class="toast-stack">
        <div v-for="t in Toast.toasts" :key="t.id" class="toast" :class="t.type">
          <div class="toast-message">{{ t.message }}</div>
          <button class="toast-close" @click="Toast.toasts.splice(Toast.toasts.indexOf(t),1)">&times;</button>
        </div>
      </div>
    `,
  });
  app.component('FormFields', FormFields);
  return app;
}

const FormFields = {
  props: ['fields', 'form', 'tags', 'kvs', 'remoteOptions'],
  emits: ['addTag', 'removeTag', 'addKv', 'removeKv'],
  setup(props, { emit }) {
    const kvDropdownKey = Vue.ref(null);

    function pickOption(fieldKey, opt, kKey) {
      props.kvs[kKey] = opt;
      kvDropdownKey.value = null;
    }

    function onKvKeyInput(fieldKey, kKey, val) {
      props.kvs[kKey] = val;
      kvDropdownKey.value = fieldKey;
    }

    function onKvKeyFocus(fieldKey) {
      kvDropdownKey.value = fieldKey;
    }

    function onKvKeyBlur() {
      setTimeout(() => { kvDropdownKey.value = null; }, 150);
    }

    return { formatValue, kvDropdownKey, pickOption, onKvKeyInput, onKvKeyFocus, onKvKeyBlur };
  },
  template: `
    <div class="form-row">
      <div class="form-group" v-for="field in fields" :key="field.key" :style="field.type==='textarea'?'grid-column:1 / -1;':''">
        <label class="form-label">
          {{ field.label }}
          <span v-if="field.required" class="required">*</span>
        </label>

        <select v-if="field.type === 'select'" class="form-select" v-model="form[field.key]">
          <option value="">— Select —</option>
          <option v-for="opt in field.options" :key="opt" :value="opt">{{ opt }}</option>
        </select>

        <select v-else-if="field.type === 'remote-select'" class="form-select" v-model="form[field.key]">
          <option value="">— Select —</option>
          <option v-for="opt in (remoteOptions[field.key]||[])" :key="opt" :value="opt">
            {{ opt }}
          </option>
        </select>

        <label v-else-if="field.type === 'checkbox'" class="checkbox-wrap">
          <input type="checkbox" v-model="form[field.key]" />
          <span>{{ field.hint || field.label }}</span>
        </label>

        <div v-else-if="field.type === 'tags'" class="tag-input" @click="$refs['tagInput-'+field.key] && $refs['tagInput-'+field.key].focus()">
          <span v-for="(tag,idx) in (form[field.key]||[])" :key="idx" class="tag-item">
            {{ tag }}
            <button type="button" @click.stop="$emit('removeTag', field.key, idx)">&times;</button>
          </span>
          <input :ref="'tagInput-'+field.key" v-model="tags[field.key]"
                 @keydown.enter.prevent="$emit('addTag', field.key, field.key, tags[field.key])"
                 :placeholder="'Add ' + field.label + '...'" />
        </div>

        <div v-else-if="field.type === 'remote-tags'">
          <div class="tag-input" @click="$refs['remoteTagSel-'+field.key] && $refs['remoteTagSel-'+field.key].click()">
            <span v-for="(tag,idx) in (form[field.key]||[])" :key="idx" class="tag-item">
              {{ tag }}
              <button type="button" @click.stop="form[field.key].splice(idx,1)">&times;</button>
            </span>
            <select :ref="'remoteTagSel-'+field.key" class="remote-tag-select" @change="if($event.target.value){if(!form[field.key])form[field.key]=[];if(!form[field.key].includes($event.target.value))form[field.key].push($event.target.value);$event.target.value='';}">
              <option value="">+ Add...</option>
              <option v-for="opt in (remoteOptions[field.key]||[])" :key="opt" :value="opt" :disabled="(form[field.key]||[]).includes(opt)">{{ opt }}</option>
            </select>
          </div>
        </div>

        <div v-else-if="field.type === 'kv'">
          <div class="kv-edit-list">
            <div v-for="(v,k) in (form[field.key]||{})" :key="k" class="kv-edit-item">
              <span class="kv-edit-key">{{ k }}</span>
              <span class="kv-edit-val">{{ v }}</span>
              <button type="button" class="kv-edit-del" @click="$emit('removeKv', field.key, k)">&times;</button>
            </div>
            <div v-if="!Object.keys(form[field.key]||{}).length" class="kv-edit-empty">No items yet</div>
          </div>
          <div class="kv-edit-add">
            <div style="position:relative;flex:1;min-width:120px;">
              <input class="form-input" :ref="'kvk-'+field.key" :value="kvs['k-'+field.key]" @input="onKvKeyInput(field.key, 'k-'+field.key, $event.target.value)" @focus="onKvKeyFocus(field.key)" @blur="onKvKeyBlur()" placeholder="key" @keydown.enter.prevent="$refs['kvv-'+field.key].focus()" autocomplete="off" />
              <div v-if="kvDropdownKey === field.key && (field.keyOptions||[]).length" class="kv-dropdown">
                <div v-for="opt in field.keyOptions.filter(o => o.toLowerCase().includes((kvs['k-'+field.key]||'').toLowerCase()))" :key="opt" class="kv-dropdown-item" @mousedown.prevent="pickOption(field.key, opt, 'k-'+field.key)">
                  {{ opt }}
                </div>
              </div>
            </div>
            <input class="form-input" :ref="'kvv-'+field.key" v-model="kvs['v-'+field.key]" placeholder="value" @keydown.enter.prevent="$emit('addKv', field.key, 'k-'+field.key, 'v-'+field.key)" />
            <button type="button" class="btn btn-secondary btn-md" @click="$emit('addKv', field.key, 'k-'+field.key, 'v-'+field.key)">+ Add</button>
          </div>
        </div>

        <div v-else-if="field.type === 'textlist'" class="textlist">
          <div v-for="(_, idx) in form[field.key]" :key="idx" class="textlist-item">
            <div style="display:flex;gap:8px;align-items:flex-start;">
              <textarea class="form-textarea" v-model="form[field.key][idx]" :placeholder="field.hint || ''" style="flex:1;"></textarea>
              <button type="button" class="btn btn-danger btn-sm" @click="form[field.key].splice(idx,1)" v-if="form[field.key].length > 1">&times;</button>
            </div>
          </div>
          <button type="button" class="btn btn-secondary btn-sm" @click="form[field.key].push('')">+ Add PEM</button>
        </div>

        <textarea v-else-if="field.type === 'textarea'" class="form-textarea" v-model="form[field.key]" :placeholder="field.hint || ''"></textarea>
        <input v-else :type="field.type" class="form-input" v-model="form[field.key]" :placeholder="field.hint || ''" />

        <div v-if="field.hint && field.type !== 'checkbox'" class="form-hint">{{ field.hint }}</div>
      </div>
    </div>
  `,
};
