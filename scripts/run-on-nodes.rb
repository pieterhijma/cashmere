
$debug = true

if ARGV.length == 0
    puts "need a node specification"
    exit 1
end

$nodes = []
$extra_params = []

class String
    def is_i?
	!!(self =~ /\A[-+]?[0-9]+\z/)
    end
end

def ask
    puts "Continue [Y/n]"
    answer = $stdin.gets.chomp
    case answer
    when "", "Y"
	return true
    else
	return false
    end
end

def check_node(node)
    if not NODE_MAP.keys.include? node
	print node, " not in ", NODE_MAP.keys.join(", "), "\n"
	exit 1
    end
end

def get_nodes(node, numberString) 
    check_node(node)

    available_nodes = NODE_MAP[node] - NODES_NOT_AVAILABLE

    if numberString == "all"
	number = NODE_MAP[node].length
    elsif numberString.is_i?
	number = numberString.to_i
    else
	print "unrecognized number for #{node}"
	exit 1
    end

    selected_nodes = available_nodes[0, number]
    if selected_nodes.length < number
	print "can only find ", selected_nodes.length, " ", node, " nodes\n"
	if $debug
	    print "available nodes: ", available_nodes.join(", "), "\n"
	    print "selected_nodes: ", selected_nodes.join(", "), "\n"
	end
	if not ask
	    exit 0
	end
    end
    print "selecting ", selected_nodes.length, " ", node, 
	" node#{selected_nodes.length == 1 ? "" : "s"}\n"
    selected_nodes = selected_nodes.map { |n| get_queue(node) + "@" + n }
    $nodes += selected_nodes
    
    if selected_nodes.length > 0
	$extra_params += [EXTRA_PARAM_MAP[node]]
    end
end


def isNodeSpec(arg)
    arg_spec = arg.split("=")
    arg_spec.length == 2 && NODE_MAP.keys.include?(arg_spec[0])
end

$node_specs, other_args = ARGV.partition { |a| isNodeSpec a }

if other_args.length < 3
    puts "not enough arguments"
    exit 1
end

if $node_specs.length == 0
    puts "need a node specification: <node=1> or <node=all>"
    puts "node types: #{NODE_MAP.keys.join(", ")}"
    exit 1
end

basedir = other_args[0]
jar = other_args[1]
$className = other_args[2]
$rest = other_args[3..-1]
$classpath = `#{$cashmere}/scripts/create-class-path #{basedir} #{jar}`.chomp
$port = ENV['CASHMERE_PORT']


