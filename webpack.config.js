const fs = require("fs");
const path = require("path");
const { SourceMapConsumer, SourceMapGenerator } = require("source-map-js");

const entryMapPath = path.resolve(__dirname, "target/public/cljs/app.js.map");

// Closure Compiler emits out-of-order column deltas in app.js.map, which makes
// webpack-sources rewind and duplicate tokens in app.js when chaining column maps.
class SortClosureSourceMapPlugin {
    apply(compiler) {
        compiler.hooks.beforeRun.tap("SortClosureSourceMapPlugin", () => {
            if (fs.existsSync(entryMapPath)) {
                const raw = JSON.parse(fs.readFileSync(entryMapPath, "utf8"));
                const sorted = SourceMapGenerator.fromSourceMap(new SourceMapConsumer(raw)).toJSON();
                fs.writeFileSync(entryMapPath, JSON.stringify(sorted));
            }
        });
    }
}

module.exports = {
    mode: "production",
    devtool: "hidden-source-map",
    context: path.resolve(__dirname),
    entry: path.resolve(__dirname, "target/public/cljs/app.js"),
    output: {
        path: path.resolve(__dirname, "target/public/cljs"),
        filename: "app.js",
        globalObject: "window",
    },
    plugins: [new SortClosureSourceMapPlugin()],
    module: {
        rules: [
            {
                test: /\.js$/,
                enforce: "pre",
                use: ["source-map-loader"],
            },
        ],
    },
    ignoreWarnings: [/Failed to parse source map/],
    resolve: {
        alias: {
            react: path.resolve("./node_modules/react"),
            "react-dom": path.resolve("./node_modules/react-dom"),
        },
    },
    optimization: {
        minimize: false,
        moduleIds: "named",
    },
};
