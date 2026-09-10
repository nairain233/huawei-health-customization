const fs = require('node:fs');

// 标题兼容 v1.1 与 1.1；发布标签额外兼容 versionCode-versionName。
const normalize = (version) => version.replace(/^v/, '');
const tag = process.env.RELEASE_TAG;
if (!tag) throw new Error('缺少 RELEASE_TAG');
const versionName = normalize(tag.replace(/^\d+-(?=v?\d+\.)/, ''));

const changelog = fs.readFileSync('changelog.md', 'utf8');
const headings = [...changelog.matchAll(/^##[ \t]+(.+?)\r?$/gm)];
const matches = headings.filter((heading) => {
  const version = heading[1].trim().split(/\s+/)[0];
  return normalize(version) === versionName;
});
if (matches.length !== 1) {
  throw new Error(`changelog.md 必须包含唯一的对应版本二级标题：${versionName}（标签 ${tag}）`);
}

const heading = matches[0];
const next = headings[headings.indexOf(heading) + 1];
const body = changelog.slice(
  heading.index + heading[0].length,
  next ? next.index : changelog.length,
).trim();
if (!body) throw new Error('对应版本的更新日志为空');

fs.writeFileSync('release-notes.md', `${body}\n`, 'utf8');
