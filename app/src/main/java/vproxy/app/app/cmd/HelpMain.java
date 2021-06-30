package vproxy.app.app.cmd;

import vproxy.base.util.Tuple;

import java.util.List;

public class HelpMain {
    public static void main(String[] args) {
        StringBuilder result = new StringBuilder();
        HelpCommand.ResMan[] men = HelpCommand.ResMan.values();
        result.append("# vproxy\n\n");
        for (HelpCommand.ResMan man : men) {
            result.append("## ").append(man.res).append("\n\n");
            if (man.shortVer != null) {
                result.append("short version: ").append(man.shortVer).append("\n\n");
            }
            result.append("description: ").append(man.descr).append("\n\n");
            List<HelpCommand.ResActMan> acts = man.acts;
            for (int j = 0, actsSize = acts.size(); j < actsSize; j++) {
                HelpCommand.ResActMan act = acts.get(j);
                result.append("### ").append(j + 1).append(". ").append(act.act).append("\n\n");
                result.append("description: ").append(act.descr).append("\n\n");

                if (act.flagDescr != null && !act.flagDescr.isEmpty()) {
                    result.append("#### flag: " + "\n\n");
                    List<HelpCommand.ResActFlagMan> flagDescr = act.flagDescr;
                    for (int i = 0, flagDescrSize = flagDescr.size(); i < flagDescrSize; i++) {
                        HelpCommand.ResActFlagMan resActFlagMan = flagDescr.get(i);
                        result.append("##### ").append(i + 1).append(". ").append(resActFlagMan.flag).append("\n\n");
                        result.append("description: ").append(resActFlagMan.descr).append("\n\n");
                        result.append("optional: ").append(resActFlagMan.optional).append("\n\n");
                        result.append("default: ").append(resActFlagMan.isDefault).append("\n\n");
                    }
                }

                if (act.paramDescr != null && !act.paramDescr.isEmpty()) {
                    result.append("#### parameter description:\n\n");
                    List<HelpCommand.ResActParamMan> paramDescr = act.paramDescr;
                    for (int i = 0, paramDescrSize = paramDescr.size(); i < paramDescrSize; i++) {
                        HelpCommand.ResActParamMan resActParamMan = paramDescr.get(i);
                        result.append("##### ").append(i + 1).append(". ").append(resActParamMan.param).append("\n\n");
                        result.append("description: ").append(resActParamMan.descr).append("\n\n");
                        result.append("optional: ").append(resActParamMan.optional).append("\n\n");
                        result.append("default value: ").append(resActParamMan.defaultValue).append("\n\n");
                    }
                }

                if (act.examples != null && !act.examples.isEmpty()) {
                    result.append("examples: \n\n");
                    for (Tuple<String, String> example : act.examples) {
                        result.append("```\n");
                        result.append("$ ").append(example.left).append("\n");
                        result.append(example.right).append("\n");
                        result.append("```\n\n");
                    }
                }
            }
        }
        System.out.println(result.toString());
    }
}
