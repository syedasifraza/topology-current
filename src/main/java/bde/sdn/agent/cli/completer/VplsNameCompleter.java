/*
 * Copyright 2015-present Open Networking Laboratory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package bde.sdn.agent.cli.completer;

import com.google.common.collect.Lists;
import org.onosproject.cli.AbstractChoicesCompleter;
import bde.sdn.agent.config.VplsConfigService;

import java.util.List;

import static org.onosproject.cli.AbstractShellCommand.get;

/**
 * VPLS name completer.
 */
public class VplsNameCompleter extends AbstractChoicesCompleter {

    @Override
    public List<String> choices() {
        VplsConfigService vplsConfigService = get(VplsConfigService.class);
        return Lists.newArrayList(vplsConfigService.vplsNames());
    }
}
